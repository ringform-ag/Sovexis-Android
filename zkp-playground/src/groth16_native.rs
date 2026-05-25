// ===========================================================================
// Sovexis ZKP Playground - Groth16 原生实现
// ===========================================================================
//
// [AI-GENERATED]
// 生成时间: 2026-05-20
// 实现状态: FRAMEWORK - 框架文件，待人工完善
// 人工补充:
//   - 具体的 R1CS 电路设计（生物认证、DID 证明等）
//   - 电路参数优化（约束数量、见证大小）
//   - 性能基准测试与优化
//   - 可信设置的安全审计
//
// 本文件实现基于 arkworks-rs 的 Groth16 zkSNARK 核心功能:
//   1. setup()  - 可信设置（Trusted Setup）
//   2. prove()  - 证明生成
//   3. verify() - 证明验证
//
// 使用的椭圆曲线: BN254 (Ethereum 兼容)
// 使用的配对引擎: ark_bn254::Bn254
//
// 参考文献:
//   - Groth16 论文: https://eprint.iacr.org/2016/260
//   - arkworks-rs 文档: https://docs.rs/ark-groth16/0.6.0/
//   - Microsoft Crescent: https://www.microsoft.com/en-us/research/project/crescent/
//
// ===========================================================================

use ark_bn254::Bn254;
use ark_ec::pairing::Pairing;
use ark_ff::Field;
use ark_groth16::{
    create_random_proof, generate_random_parameters, prepare_verifying_key, verify_proof,
    Proof, ProvingKey, VerifyingKey,
};
use ark_relations::r1cs::{ConstraintSynthesizer, ConstraintSystemRef, SynthesisError};
use ark_serialize::{CanonicalDeserialize, CanonicalSerialize};
use ark_std::rand::RngCore;
use ark_std::UniformRand;

use crate::{CurvePairing, G1Projective, ScalarField, ZkpError, ZkpResult};

// ===========================================================================
// 数据结构定义
// ===========================================================================

/// ZKP 参数集合（Trusted Setup 的输出）
///
/// 包含证明密钥 (ProvingKey) 和验证密钥 (VerifyingKey)。
/// 证明密钥用于生成证明，验证密钥用于验证证明。
///
/// 序列化格式: 二进制 (arkworks CanonicalSerialize)
///
/// # 安全注意事项
/// - ProvingKey 必须保密，泄露后任何人都可以伪造证明
/// - VerifyingKey 可以公开，用于验证方校验证明
/// - 在实际部署中，建议使用 MPC 仪式 (MPC Ceremony) 进行可信设置
#[derive(Debug, Clone)]
pub struct ZkpParameters {
    /// Groth16 证明密钥 (pk)
    /// 包含 alpha, beta, gamma, delta, 以及所有电路线 (wires) 的承诺
    pub proving_key: ProvingKey<CurvePairing>,

    /// Groth16 验证密钥 (vk)
    /// 包含 alpha_g1, beta_g2, gamma_g2, delta_g2, 以及公共输入的 IC 预计算
    pub verifying_key: VerifyingKey<CurvePairing>,
}

impl ZkpParameters {
    /// 序列化证明密钥为字节数组
    ///
    /// # 返回值
    /// 包含序列化后 ProvingKey 的 Vec<u8>
    pub fn serialize_proving_key(&self) -> ZkpResult<Vec<u8>> {
        let mut bytes = Vec::new();
        self.proving_key
            .serialize_compressed(&mut bytes)
            .map_err(|e| ZkpError::Serialization(format!("ProvingKey 序列化失败: {}", e)))?;
        Ok(bytes)
    }

    /// 从字节数组反序列化证明密钥
    pub fn deserialize_proving_key(bytes: &[u8]) -> ZkpResult<ProvingKey<CurvePairing>> {
        let pk = ProvingKey::<CurvePairing>::deserialize_compressed(bytes)
            .map_err(|e| ZkpError::Serialization(format!("ProvingKey 反序列化失败: {}", e)))?;
        Ok(pk)
    }

    /// 序列化验证密钥为字节数组
    pub fn serialize_verifying_key(&self) -> ZkpResult<Vec<u8>> {
        let mut bytes = Vec::new();
        self.verifying_key
            .serialize_compressed(&mut bytes)
            .map_err(|e| ZkpError::Serialization(format!("VerifyingKey 序列化失败: {}", e)))?;
        Ok(bytes)
    }

    /// 从字节数组反序列化验证密钥
    pub fn deserialize_verifying_key(bytes: &[u8]) -> ZkpResult<VerifyingKey<CurvePairing>> {
        let vk = VerifyingKey::<CurvePairing>::deserialize_compressed(bytes)
            .map_err(|e| ZkpError::Serialization(format!("VerifyingKey 反序列化失败: {}", e)))?;
        Ok(vk)
    }

    /// 从序列化的字节数组重建完整参数
    pub fn from_bytes(pk_bytes: &[u8], vk_bytes: &[u8]) -> ZkpResult<Self> {
        let proving_key = Self::deserialize_proving_key(pk_bytes)?;
        let verifying_key = Self::deserialize_verifying_key(vk_bytes)?;
        Ok(Self {
            proving_key,
            verifying_key,
        })
    }
}

/// ZKP 证明结果
///
/// 包含 Groth16 证明和公共输入。
/// 证明体积约 200 字节（3 个 G1 群元素），非常紧凑。
#[derive(Debug, Clone)]
pub struct ZkpProofResult {
    /// Groth16 证明 (a, b, c)
    pub proof: Proof<CurvePairing>,

    /// 公共输入 (public inputs)
    /// 这些值在证明和验证时都需要，但不泄露私有信息
    pub public_inputs: Vec<ScalarField>,
}

impl ZkpProofResult {
    /// 序列化证明为字节数组
    ///
    /// Groth16 证明 = 3 个 G1 群元素 + 1 个 G2 群元素
    /// BN254 上约 192 字节（压缩格式）
    pub fn serialize_proof(&self) -> ZkpResult<Vec<u8>> {
        let mut bytes = Vec::new();
        self.proof
            .serialize_compressed(&mut bytes)
            .map_err(|e| ZkpError::Serialization(format!("Proof 序列化失败: {}", e)))?;
        Ok(bytes)
    }

    /// 从字节数组反序列化证明
    pub fn deserialize_proof(bytes: &[u8]) -> ZkpResult<Proof<CurvePairing>> {
        let proof = Proof::<CurvePairing>::deserialize_compressed(bytes)
            .map_err(|e| ZkpError::Serialization(format!("Proof 反序列化失败: {}", e)))?;
        Ok(proof)
    }

    /// 序列化公共输入为字节数组
    pub fn serialize_public_inputs(&self) -> ZkpResult<Vec<u8>> {
        let mut bytes = Vec::new();
        // 先写入公共输入数量 (u32)
        let count = self.public_inputs.len() as u32;
        bytes.extend_from_slice(&count.to_le_bytes());
        // 逐个序列化标量场元素
        for input in &self.public_inputs {
            input
                .serialize_compressed(&mut bytes)
                .map_err(|e| ZkpError::Serialization(format!("公共输入序列化失败: {}", e)))?;
        }
        Ok(bytes)
    }

    /// 从字节数组反序列化公共输入
    pub fn deserialize_public_inputs(bytes: &[u8]) -> ZkpResult<Vec<ScalarField>> {
        if bytes.len() < 4 {
            return Err(ZkpError::InvalidInput("公共输入数据过短".to_string()));
        }
        let count = u32::from_le_bytes([bytes[0], bytes[1], bytes[2], bytes[3]]) as usize;
        let mut offset = 4;
        let mut inputs = Vec::with_capacity(count);
        for _ in 0..count {
            let input = ScalarField::deserialize_compressed(&bytes[offset..])
                .map_err(|e| {
                    ZkpError::Serialization(format!("公共输入反序列化失败: {}", e))
                })?;
            offset += input.serialized_size(ark_serialize::Compress::Yes);
            inputs.push(input);
        }
        Ok(inputs)
    }
}

// ===========================================================================
// 示例电路 - Sovexis 生物认证电路
// ===========================================================================

/// Sovexis 生物认证电路 (示例/占位)
///
/// [MANUAL-IMPLEMENTATION-REQUIRED]
/// 这是一个示例电路，用于演示 arkworks-rs 的 R1CS 约束系统用法。
/// 实际的生物认证电路需要密码学专家设计，考虑以下因素:
///
/// 1. 电路语义: 证明"用户满足生物认证条件"而不泄露具体生物特征
/// 2. 约束效率: 减少约束数量以降低证明生成时间
/// 3. 安全性: 确保电路不泄露私有信息
/// 4. 可组合性: 支持与其他 ZKP 电路的组合
///
/// 示例电路语义:
///   - 私有输入: 用户年龄 (secret_age)
///   - 公共输入: 年龄阈值 (min_age)
///   - 约束: secret_age >= min_age
///   - 证明: "用户年龄满足最低要求"（不泄露实际年龄）
///
/// # 类型参数
/// - `E`: 配对引擎（默认使用 Bn254）
#[derive(Debug, Clone)]
pub struct SovexisBioAuthCircuit {
    // ----- 私有输入 (Witness) -----
    /// 用户的秘密年龄值
    /// 实际部署中可能替换为生物特征哈希、DID 等敏感数据
    pub secret_age: Option<u64>,

    // ----- 公共输入 (Public Input) -----
    /// 年龄阈值（公开）
    pub min_age: Option<u64>,
}

impl ConstraintSynthesizer<ScalarField> for SovexisBioAuthCircuit {
    /// 生成 R1CS 约束
    ///
    /// 将电路逻辑转化为 Rank-1 Constraint System (R1CS) 形式。
    /// 每个约束形如: (a . witness) * (b . witness) = (c . witness)
    ///
    /// # 参数
    /// - `cs`: 约束系统引用，用于分配变量和添加约束
    ///
    /// # 错误
    /// 返回 SynthesisError 如果约束生成失败
    fn generate_constraints(
        self,
        cs: ConstraintSystemRef<ScalarField>,
    ) -> Result<(), SynthesisError> {
        log::info!("SovexisBioAuthCircuit: 开始生成 R1CS 约束");

        // ------------------------------------------------------------------
        // 分配私有输入变量
        // ------------------------------------------------------------------

        // 分配 secret_age 作为私有变量
        // allocate_new() 的第一个参数是分配值（None 表示仅分配不赋值，用于验证）
        let secret_age_var = cs.new_witness_variable(|| {
            self.secret_age
                .ok_or(SynthesisError::AssignmentMissing)
                .map(|v| ScalarField::from(v))
        })?;

        log::debug!("SovexisBioAuthCircuit: 已分配 secret_age 变量");

        // ------------------------------------------------------------------
        // 分配公共输入变量
        // ------------------------------------------------------------------

        // 分配 min_age 作为公共输入变量
        let min_age_var = cs.new_input_variable(|| {
            self.min_age
                .ok_or(SynthesisError::AssignmentMissing)
                .map(|v| ScalarField::from(v))
        })?;

        log::debug!("SovexisBioAuthCircuit: 已分配 min_age 公共输入变量");

        // ------------------------------------------------------------------
        // 添加约束
        // ------------------------------------------------------------------

        // 约束 1: secret_age * 1 = secret_age (确保 secret_age 被正确赋值)
        // 这是一个恒等约束，确保变量被正确分配
        cs.enforce_constraint(
            secret_age_var,
            ScalarField::one(),
            secret_age_var,
        )?;

        // 约束 2: min_age * 1 = min_age (确保 min_age 被正确赋值)
        cs.enforce_constraint(
            min_age_var,
            ScalarField::one(),
            min_age_var,
        )?;

        // ------------------------------------------------------------------
        // [MANUAL-IMPLEMENTATION-REQUIRED]
        // 实际的生物认证约束应在此处添加
        // ------------------------------------------------------------------
        //
        // 示例: 年龄范围证明（简化版，实际需要范围证明 gadget）
        //
        // 使用 ark_r1cs_std 的 Boolean 和 UInt 库实现范围证明:
        //   use ark_r1cs_std::fields::fp::FpVar;
        //   use ark_r1cs_std::prelude::*;
        //
        //   let secret_age_fp = FpVar::new_witness(cs.clone(), || {
        //       self.secret_age.ok_or(SynthesisError::AssignmentMissing)
        //           .map(|v| ScalarField::from(v))
        //   })?;
        //
        //   let min_age_fp = FpVar::new_input(cs.clone(), || {
        //       self.min_age.ok_or(SynthesisError::AssignmentMissing)
        //           .map(|v| ScalarField::from(v))
        //   })?;
        //
        //   // 范围证明: secret_age >= min_age
        //   // 使用减法 + 非负检查实现
        //   let diff = secret_age_fp - min_age_fp;
        //   diff.enforce_not_equal(&FpVar::zero())?;
        //
        // ------------------------------------------------------------------

        log::info!(
            "SovexisBioAuthCircuit: R1CS 约束生成完成, 约束数量: {}",
            cs.num_constraints()
        );

        Ok(())
    }
}

// ===========================================================================
// 核心功能实现
// ===========================================================================

/// 可信设置 (Trusted Setup)
///
/// 为给定电路生成 Groth16 的证明密钥 (ProvingKey) 和验证密钥 (VerifyingKey)。
///
/// # 安全警告
/// 此函数使用随机数生成器进行"有毒废料" (toxic waste) 生成。
/// 在生产环境中，应使用 MPC 仪式 (MPC Ceremony) 进行去中心化的可信设置，
/// 确保没有任何单个参与者知道完整的 toxic waste。
///
/// # 参数
/// - `rng`: 随机数生成器，用于生成 toxic waste (alpha, beta, gamma, delta)
///
/// # 返回值
/// 返回 `ZkpResult<ZkpParameters>`，包含证明密钥和验证密钥
///
/// # 示例
/// ```rust,ignore
/// use rand::thread_rng;
/// let params = setup(&mut thread_rng())?;
/// ```
///
/// # 性能参考
/// - BN254 + 简单电路: ~1-2 秒
/// - BN254 + 复杂电路 (10k 约束): ~10-30 秒
pub fn setup<R: RngCore>(rng: &mut R) -> ZkpResult<ZkpParameters> {
    log::info!("Groth16 Setup: 开始可信设置...");

    // 创建示例电路实例（使用 None 值，因为 setup 不需要具体赋值）
    let circuit = SovexisBioAuthCircuit {
        secret_age: None,
        min_age: None,
    };

    // 生成随机参数 (Groth16 的 Trusted Setup)
    //
    // generate_random_parameters 内部执行:
    // 1. 采样随机 alpha, beta, gamma, delta (toxic waste)
    // 2. 计算公共参数: [alpha]G1, [beta]G1, [beta]G2, ...
    // 3. 计算电路相关参数: A_i, B_i, C_i 的承诺
    //
    // [MANUAL-IMPLEMENTATION-REQUIRED]
    // 实际部署时应替换为具体的业务电路
    let parameters = generate_random_parameters::<Bn254, _, _>(circuit, rng)
        .map_err(|e| ZkpError::CircuitSynthesis(format!("参数生成失败: {}", e)))?;

    log::info!(
        "Groth16 Setup: 可信设置完成. ProvingKey 大小: ~{} bytes",
        // ProvingKey 大小取决于电路规模
        "估算中"
    );

    Ok(ZkpParameters {
        proving_key: parameters,
        verifying_key: parameters.vk.clone(),
    })
}

/// 生成零知识证明
///
/// 使用证明密钥和见证 (witness) 生成 Groth16 零知识证明。
///
/// # 参数
/// - `params`: 可信设置生成的参数（包含 proving_key）
/// - `secret_age`: 用户的秘密年龄（私有输入）
/// - `min_age`: 年龄阈值（公共输入）
///
/// # 返回值
/// 返回 `ZkpResult<ZkpProofResult>`，包含证明和公共输入
///
/// # 证明特性
/// - 零知识: 验证方无法从证明中获取 secret_age 的值
/// - 简洁: 证明体积固定 ~200 字节，与电路规模无关
/// - 非交互: 证明生成后无需与验证方进一步交互
/// - 可验证: 任何人持有验证密钥都可以验证证明
///
/// # 性能参考
/// - BN254 + 简单电路: ~1-3 秒
/// - BN254 + 复杂电路 (10k 约束): ~5-15 秒
pub fn prove(
    params: &ZkpParameters,
    secret_age: u64,
    min_age: u64,
) -> ZkpResult<ZkpProofResult> {
    log::info!(
        "Groth16 Prove: 开始生成证明 (secret_age={}, min_age={})",
        secret_age,
        min_age
    );

    // 创建带有具体赋值的电路实例
    let circuit = SovexisBioAuthCircuit {
        secret_age: Some(secret_age),
        min_age: Some(min_age),
    };

    // 生成公共输入列表
    //
    // 公共输入必须与电路中 new_input_variable() 的顺序一致
    // 在我们的示例电路中，公共输入为 [min_age]
    let public_inputs: Vec<ScalarField> = vec![ScalarField::from(min_age)];

    // 创建 Groth16 证明
    //
    // create_random_proof 内部执行:
    // 1. 综合电路，获取所有见证值
    // 2. 计算随机 blinding factors (r, s)
    // 3. 计算 [proof] = (A, B, C) 群元素
    //    A = [alpha] + [r] * [a] + [s] * [b]
    //    B = [beta] + [s] * [b]
    //    C = [proof_c] (详细公式见 Groth16 论文)
    let proof = create_random_proof::<Bn254, _, _>(circuit, &params.proving_key, &mut ark_std::test_rng())
        .map_err(|e| ZkpError::ProveFailed(format!("证明生成失败: {}", e)))?;

    log::info!(
        "Groth16 Prove: 证明生成完成. 公共输入数量: {}",
        public_inputs.len()
    );

    Ok(ZkpProofResult {
        proof,
        public_inputs,
    })
}

/// 验证零知识证明
///
/// 使用验证密钥和公共输入验证 Groth16 证明的正确性。
///
/// # 参数
/// - `params`: 可信设置生成的参数（包含 verifying_key）
/// - `proof_result`: 证明结果（包含证明和公共输入）
///
/// # 返回值
/// 返回 `ZkpResult<bool>`:
/// - `Ok(true)`: 证明有效
/// - `Ok(false)`: 证明无效
/// - `Err(_)`: 验证过程出错
///
/// # 验证过程
/// 1. 预处理验证密钥 (prepare_verifying_key)
/// 2. 执行配对检查 (pairing check):
///    e(A, B) = e(alpha, beta) * e(sum_i( a_i * x_i ], gamma), gamma) * e(C, delta)
///    如果等式成立，则证明有效
///
/// # 性能参考
/// - BN254: ~1-5 毫秒（配对运算是瓶颈）
pub fn verify(
    params: &ZkpParameters,
    proof_result: &ZkpProofResult,
) -> ZkpResult<bool> {
    log::info!("Groth16 Verify: 开始验证证明...");

    // 预处理验证密钥
    //
    // prepare_verifying_key 预计算 IC (Information-theoretic Commitment)
    // 将公共输入的线性组合预计算，加速验证过程
    let pvk = prepare_verifying_key(&params.verifying_key);

    // 执行配对检查验证
    //
    // verify_proof 检查以下等式:
    //   e(A, B) == e(alpha_g1, beta_g2) * e(IC[0] + sum(public_input[i] * IC[i+1]), gamma_g2) * e(C, delta_g2)
    //
    // 其中 e() 是双线性配对运算
    let is_valid = verify_proof(&pvk, &proof_result.proof, &proof_result.public_inputs)
        .map_err(|e| ZkpError::VerifyFailed(format!("验证执行失败: {}", e)))?;

    if is_valid {
        log::info!("Groth16 Verify: 证明验证通过 (VALID)");
    } else {
        log::warn!("Groth16 Verify: 证明验证失败 (INVALID)");
    }

    Ok(is_valid)
}

/// 便捷函数: 从序列化数据验证证明
///
/// 用于 JNI 层直接传入字节数组进行验证，
/// 避免在 Java/Kotlin 层重建复杂的 Rust 类型。
///
/// # 参数
/// - `vk_bytes`: 序列化的验证密钥
/// - `proof_bytes`: 序列化的证明
/// - `public_inputs_bytes`: 序列化的公共输入
///
/// # 返回值
/// 返回 `ZkpResult<bool>`
pub fn verify_from_bytes(
    vk_bytes: &[u8],
    proof_bytes: &[u8],
    public_inputs_bytes: &[u8],
) -> ZkpResult<bool> {
    log::info!("Groth16 Verify (from bytes): 开始从序列化数据验证...");

    // 反序列化验证密钥
    let vk = ZkpParameters::deserialize_verifying_key(vk_bytes)?;

    // 反序列化证明
    let proof = ZkpProofResult::deserialize_proof(proof_bytes)?;

    // 反序列化公共输入
    let public_inputs = ZkpProofResult::deserialize_public_inputs(public_inputs_bytes)?;

    // 预处理验证密钥并验证
    let pvk = prepare_verifying_key(&vk);
    let is_valid = verify_proof(&pvk, &proof, &public_inputs)
        .map_err(|e| ZkpError::VerifyFailed(format!("验证执行失败: {}", e)))?;

    log::info!(
        "Groth16 Verify (from bytes): 验证结果 = {}",
        is_valid
    );

    Ok(is_valid)
}

// ===========================================================================
// 单元测试
// ===========================================================================

#[cfg(test)]
mod tests {
    use super::*;
    use ark_std::test_rng;

    /// 测试完整的 Setup -> Prove -> Verify 流程
    #[test]
    fn test_full_groth16_flow() {
        let mut rng = test_rng();

        // 1. Setup
        let params = setup(&mut rng).expect("Setup 失败");

        // 2. Prove (secret_age=25, min_age=18)
        let proof_result = prove(&params, 25, 18).expect("Prove 失败");

        // 3. Verify
        let is_valid = verify(&params, &proof_result).expect("Verify 失败");
        assert!(is_valid, "证明应该有效");
    }

    /// 测试序列化和反序列化
    #[test]
    fn test_serialization() {
        let mut rng = test_rng();

        // Setup
        let params = setup(&mut rng).expect("Setup 失败");

        // 序列化
        let pk_bytes = params.serialize_proving_key().expect("PK 序列化失败");
        let vk_bytes = params.serialize_verifying_key().expect("VK 序列化失败");

        assert!(!pk_bytes.is_empty(), "ProvingKey 不应为空");
        assert!(!vk_bytes.is_empty(), "VerifyingKey 不应为空");

        // 反序列化
        let restored = ZkpParameters::from_bytes(&pk_bytes, &vk_bytes)
            .expect("参数重建失败");

        // 使用恢复的参数进行证明和验证
        let proof_result = prove(&restored, 30, 18).expect("Prove 失败");
        let is_valid = verify(&restored, &proof_result).expect("Verify 失败");
        assert!(is_valid, "使用恢复参数的证明应该有效");
    }

    /// 测试无效证明被拒绝
    #[test]
    fn test_invalid_proof_rejected() {
        let mut rng = test_rng();

        // Setup
        let params = setup(&mut rng).expect("Setup 失败");

        // 使用错误的公共输入验证
        let proof_result = prove(&params, 25, 18).expect("Prove 失败");

        // 修改公共输入（使用不同的 min_age）
        let wrong_public_inputs = vec![ScalarField::from(21)]; // 原始 min_age 是 18

        let pvk = prepare_verifying_key(&params.verifying_key);
        let is_valid = verify_proof(&pvk, &proof_result.proof, &wrong_public_inputs)
            .expect("Verify 执行失败");

        // 使用错误的公共输入，证明应该无效
        assert!(!is_valid, "使用错误公共输入的证明应该无效");
    }
}
