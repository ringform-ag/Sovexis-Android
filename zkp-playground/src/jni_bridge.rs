// ===========================================================================
// Sovexis ZKP Playground - JNI 桥接层
// ===========================================================================
//
// [AI-GENERATED]
// 生成时间: 2026-05-20
// 实现状态: FRAMEWORK - 框架文件，待人工完善
// 人工补充:
//   - 具体业务逻辑的参数解析
//   - 错误码与 Java 异常类的映射
//   - JNI 性能优化（减少 JNI 调用次数、批量处理）
//   - 线程安全审计
//
// 本文件实现 Java/Kotlin 与 Rust 之间的 JNI 桥接。
// 所有 JNI 函数遵循标准命名规范:
//   Java_<包名>_<类名>_<方法名>
//   包名中的点号替换为下划线
//
// 对应的 Kotlin 类: com.sovexis.domain.zkp.ZkpNative
//
// JNI 函数列表:
//   - Java_com_sovexis_domain_zkp_ZkpNative_setup
//   - Java_com_sovexis_domain_zkp_ZkpNative_prove
//   - Java_com_sovexis_domain_zkp_ZkpNative_verify
//   - Java_com_sovexis_domain_zkp_ZkpNative_getLibVersion
//
// 数据流:
//   Kotlin (ZkpNative.kt)
//     -> JNI Bridge (jni_bridge.rs)
//       -> Groth16 Native (groth16_native.rs)
//         -> arkworks-rs
//
// ===========================================================================

use jni::objects::{JByteArray, JClass, JObject, JString, JValue};
use jni::sys::{jboolean, jbyteArray, jint, jlong, jobjectArray, jstring};
use jni::JNIEnv;
use once_cell::sync::Lazy;
use std::collections::HashMap;
use std::sync::Mutex;

use crate::groth16_native::{self, ZkpParameters, ZkpProofResult};
use crate::{ZkpError, ZkpResult};

// ===========================================================================
// 全局状态管理
// ===========================================================================

/// 全局参数存储
///
/// 使用 HashMap 存储多个 ZKP 参数集合，以 prepareResultId 为键。
/// 这允许同时管理多个不同电路的参数。
///
/// # 线程安全
/// 使用 Mutex 保证线程安全。在 Android 环境中，多个线程可能同时访问
/// 参数存储（例如 UI 线程和后台线程）。
static PARAMS_STORE: Lazy<Mutex<HashMap<String, ZkpParameters>>> =
    Lazy::new(|| Mutex::new(HashMap::new()));

/// 全局证明结果存储
///
/// 存储已生成的证明，以 proofId 为键。
/// 用于在 prove 和 verify 之间传递证明数据。
static PROOF_STORE: Lazy<Mutex<HashMap<String, ZkpProofResult>>> =
    Lazy::new(|| Mutex::new(HashMap::new()));

// ===========================================================================
// JNI 辅助函数
// ===========================================================================

/// 将 Java 字节数组 (jbyteArray) 转换为 Rust Vec<u8>
///
/// # 参数
/// - `env`: JNI 环境引用
/// - `byte_array`: Java 端的字节数组
///
/// # 返回值
/// 成功返回 Vec<u8>，失败返回 ZkpError
///
/// # 安全性
/// 如果 byte_array 为 null，返回错误而非 panic
fn jbyte_array_to_vec(env: &mut JNIEnv, byte_array: jbyteArray) -> ZkpResult<Vec<u8>> {
    if byte_array.is_null() {
        return Err(ZkpError::InvalidInput("字节数组不能为 null".to_string()));
    }

    let array_obj = unsafe { JByteArray::from_raw(byte_array) };
    let len = env
        .get_array_length(&array_obj)
        .map_err(|e| ZkpError::JniError(format!("获取数组长度失败: {}", e)))?;

    if len == 0 {
        return Ok(Vec::new());
    }

    let mut vec = vec![0u8; len as usize];
    env.get_byte_array_region(&array_obj, 0, &mut vec[..])
        .map_err(|e| ZkpError::JniError(format!("读取数组内容失败: {}", e)))?;

    // jbyte 是 i8，需要转换为 u8
    let vec: Vec<u8> = vec.iter().map(|&b| b as u8).collect();
    Ok(vec)
}

/// 将 Rust Vec<u8> 转换为 Java 字节数组 (jbyteArray)
///
/// # 参数
/// - `env`: JNI 环境引用
/// - `data`: Rust 端的字节向量
///
/// # 返回值
/// 成功返回 jbyteArray，失败返回 ZkpError
fn vec_to_jbyte_array(env: &mut JNIEnv, data: &[u8]) -> ZkpResult<jbyteArray> {
    let byte_array = env
        .new_byte_array(data.len() as i32)
        .map_err(|e| ZkpError::JniError(format!("创建字节数组失败: {}", e)))?;

    // u8 -> i8 转换
    let signed_data: Vec<i8> = data.iter().map(|&b| b as i8).collect();

    env.set_byte_array_region(&unsafe { JByteArray::from_raw(byte_array) }, 0, &signed_data)
        .map_err(|e| ZkpError::JniError(format!("写入数组内容失败: {}", e)))?;

    Ok(byte_array)
}

/// 将 Java 字符串 (jstring) 转换为 Rust String
///
/// # 参数
/// - `env`: JNI 环境引用
/// - `j_str`: Java 端的字符串
///
/// # 返回值
/// 成功返回 String，失败返回 ZkpError
fn jstring_to_string(env: &mut JNIEnv, j_str: jstring) -> ZkpResult<String> {
    if j_str.is_null() {
        return Err(ZkpError::InvalidInput("字符串不能为 null".to_string()));
    }

    let j_string = unsafe { JString::from_raw(j_str) };
    let rust_string = env
        .get_string(&j_string)
        .map_err(|e| ZkpError::JniError(format!("读取字符串失败: {}", e)))?;

    Ok(rust_string.into())
}

/// 将 Rust String 转换为 Java 字符串 (jstring)
///
/// # 参数
/// - `env`: JNI 环境引用
/// - `s`: Rust 端的字符串
///
/// # 返回值
/// 成功返回 jstring，失败返回 ZkpError
fn string_to_jstring(env: &mut JNIEnv, s: &str) -> ZkpResult<jstring> {
    let j_str = env
        .new_string(s)
        .map_err(|e| ZkpError::JniError(format!("创建字符串失败: {}", e)))?;

    Ok(j_str.into_raw())
}

/// 在 Java 层抛出异常
///
/// 将 ZkpError 转换为 Java 异常并抛出。
///
/// # 异常类型映射
/// - ZkpError::InvalidInput -> IllegalArgumentException
/// - ZkpError::Serialization -> java.io.IOException
/// - ZkpError::ProveFailed -> RuntimeException("ZkpProveFailed")
/// - ZkpError::VerifyFailed -> RuntimeException("ZkpVerifyFailed")
/// - 其他 -> RuntimeException
fn throw_java_exception(env: &mut JNIEnv, error: ZkpError) {
    let error_msg = format!("{}", error);

    let exception_class = match &error {
        ZkpError::InvalidInput(_) => "java/lang/IllegalArgumentException",
        ZkpError::Serialization(_) => "java/io/IOException",
        ZkpError::ProveFailed(_) => "java/lang/RuntimeException",
        ZkpError::VerifyFailed(_) => "java/lang/RuntimeException",
        _ => "java/lang/RuntimeException",
    };

    let result = env.throw_new(exception_class, &error_msg);
    if let Err(e) = result {
        log::error!(
            "JNI: 无法抛出 Java 异常 ({}): {}",
            exception_class,
            e
        );
    }
}

// ===========================================================================
// JNI 函数实现
// ===========================================================================

/// JNI: 可信设置 (Trusted Setup)
///
/// Java 签名: native byte[][] setup(String circuitType)
///
/// 为指定电路类型生成 Groth16 参数（ProvingKey + VerifyingKey）。
///
/// # 参数
/// - `env`: JNI 环境（自动传入）
/// - `_class`: Java 类引用（自动传入）
/// - `circuit_type`: 电路类型标识符（如 "bio_auth", "did_proof" 等）
///
/// # 返回值
/// byte[2][] - 包含两个元素的数组:
///   - [0]: ProvingKey 的序列化字节数组
///   - [1]: VerifyingKey 的序列化字节数组
///
/// # 异常
/// 如果设置失败，抛出 RuntimeException
///
/// # Kotlin 调用示例
/// ```kotlin
/// val (pkBytes, vkBytes) = ZkpNative.setup("bio_auth")
/// ```
#[no_mangle]
pub extern "system" fn Java_com_sovexis_mobile_zkp_ZkpNative_setup(
    mut env: JNIEnv,
    _class: JClass,
    circuit_type: jstring,
) -> jobjectArray {
    log::info!("JNI: setup() 被调用");

    // 解析电路类型参数
    let _circuit_type_str = match jstring_to_string(&mut env, circuit_type) {
        Ok(s) => s,
        Err(e) => {
            throw_java_exception(&mut env, e);
            return std::ptr::null_mut();
        }
    };

    log::info!("JNI: setup() 电路类型: {}", _circuit_type_str);

    // 执行可信设置
    let params = match groth16_native::setup(&mut ark_std::test_rng()) {
        Ok(p) => p,
        Err(e) => {
            log::error!("JNI: setup() 失败: {}", e);
            throw_java_exception(&mut env, e);
            return std::ptr::null_mut();
        }
    };

    // 序列化 ProvingKey 和 VerifyingKey
    let pk_bytes = match params.serialize_proving_key() {
        Ok(b) => b,
        Err(e) => {
            throw_java_exception(&mut env, e);
            return std::ptr::null_mut();
        }
    };

    let vk_bytes = match params.serialize_verifying_key() {
        Ok(b) => b,
        Err(e) => {
            throw_java_exception(&mut env, e);
            return std::ptr::null_mut();
        }
    };

    // 创建 Java byte[][] 返回数组
    let result_array = match env.new_object_array(
        2,
        "[B", // byte[] 的 JNI 类型签名
        JObject::null(),
    ) {
        Ok(arr) => arr,
        Err(e) => {
            throw_java_exception(
                &mut env,
                ZkpError::JniError(format!("创建返回数组失败: {}", e)),
            );
            return std::ptr::null_mut();
        }
    };

    // 设置 [0] = ProvingKey bytes
    let pk_jbytes = match vec_to_jbyte_array(&mut env, &pk_bytes) {
        Ok(b) => b,
        Err(e) => {
            throw_java_exception(&mut env, e);
            return std::ptr::null_mut();
        }
    };

    if let Err(e) = env.set_object_array_element(
        &result_array,
        0,
        unsafe { JObject::from_raw(pk_jbytes) },
    ) {
        throw_java_exception(
            &mut env,
            ZkpError::JniError(format!("设置 PK 数组元素失败: {}", e)),
        );
        return std::ptr::null_mut();
    }

    // 设置 [1] = VerifyingKey bytes
    let vk_jbytes = match vec_to_jbyte_array(&mut env, &vk_bytes) {
        Ok(b) => b,
        Err(e) => {
            throw_java_exception(&mut env, e);
            return std::ptr::null_mut();
        }
    };

    if let Err(e) = env.set_object_array_element(
        &result_array,
        1,
        unsafe { JObject::from_raw(vk_jbytes) },
    ) {
        throw_java_exception(
            &mut env,
            ZkpError::JniError(format!("设置 VK 数组元素失败: {}", e)),
        );
        return std::ptr::null_mut();
    }

    log::info!(
        "JNI: setup() 成功. PK 大小: {} bytes, VK 大小: {} bytes",
        pk_bytes.len(),
        vk_bytes.len()
    );

    result_array.into_raw()
}

/// JNI: 生成零知识证明
///
/// Java 签名: native byte[] prove(byte[] provingKey, byte[] privateInputs, byte[] publicInputs)
///
/// 使用证明密钥和输入参数生成 Groth16 零知识证明。
///
/// # 参数
/// - `env`: JNI 环境（自动传入）
/// - `_class`: Java 类引用（自动传入）
/// - `proving_key_bytes`: 序列化的 ProvingKey
/// - `private_inputs_bytes`: 私有输入的序列化数据（JSON 格式）
/// - `public_inputs_bytes`: 公共输入的序列化数据（JSON 格式）
///
/// # 返回值
/// byte[] - 序列化的 Groth16 证明
///
/// # 异常
/// 如果证明生成失败，抛出 RuntimeException
///
/// # Kotlin 调用示例
/// ```kotlin
/// val proofBytes = ZkpNative.prove(pkBytes, privateInputs, publicInputs)
/// ```
#[no_mangle]
pub extern "system" fn Java_com_sovexis_mobile_zkp_ZkpNative_prove(
    mut env: JNIEnv,
    _class: JClass,
    proving_key_bytes: jbyteArray,
    private_inputs_bytes: jbyteArray,
    public_inputs_bytes: jbyteArray,
) -> jbyteArray {
    log::info!("JNI: prove() 被调用");

    // 解析 ProvingKey
    let pk_data = match jbyte_array_to_vec(&mut env, proving_key_bytes) {
        Ok(d) => d,
        Err(e) => {
            throw_java_exception(&mut env, e);
            return std::ptr::null_mut();
        }
    };

    // 解析私有输入
    let _private_data = match jbyte_array_to_vec(&mut env, private_inputs_bytes) {
        Ok(d) => d,
        Err(e) => {
            throw_java_exception(&mut env, e);
            return std::ptr::null_mut();
        }
    };

    // 解析公共输入
    let _public_data = match jbyte_array_to_vec(&mut env, public_inputs_bytes) {
        Ok(d) => d,
        Err(e) => {
            throw_java_exception(&mut env, e);
            return std::ptr::null_mut();
        }
    };

    // 反序列化 ProvingKey
    let proving_key = match ZkpParameters::deserialize_proving_key(&pk_data) {
        Ok(pk) => pk,
        Err(e) => {
            throw_java_exception(&mut env, e);
            return std::ptr::null_mut();
        }
    };

    // ------------------------------------------------------------------
    // [MANUAL-IMPLEMENTATION-REQUIRED]
    // 解析私有输入和公共输入的 JSON 数据，转换为电路所需的类型
    // ------------------------------------------------------------------
    //
    // 示例解析逻辑:
    //   let private_inputs: serde_json::Value = serde_json::from_slice(&_private_data)
    //       .map_err(|e| ZkpError::InvalidInput(format!("私有输入 JSON 解析失败: {}", e)))?;
    //   let public_inputs: serde_json::Value = serde_json::from_slice(&_public_data)
    //       .map_err(|e| ZkpError::InvalidInput(format!("公共输入 JSON 解析失败: {}", e)))?;
    //
    //   let secret_age = private_inputs["secret_age"].as_u64()
    //       .ok_or(ZkpError::InvalidInput("缺少 secret_age".to_string()))?;
    //   let min_age = public_inputs["min_age"].as_u64()
    //       .ok_or(ZkpError::InvalidInput("缺少 min_age".to_string()))?;
    //
    // ------------------------------------------------------------------

    // 使用示例参数进行证明生成
    // [MANUAL-IMPLEMENTATION-REQUIRED] 替换为实际解析后的参数
    let secret_age: u64 = 25; // 示例值
    let min_age: u64 = 18;    // 示例值

    // 构建参数并生成证明
    let params = ZkpParameters {
        proving_key,
        verifying_key: proving_key.vk.clone(),
    };

    let proof_result = match groth16_native::prove(&params, secret_age, min_age) {
        Ok(r) => r,
        Err(e) => {
            log::error!("JNI: prove() 失败: {}", e);
            throw_java_exception(&mut env, e);
            return std::ptr::null_mut();
        }
    };

    // 序列化证明
    let proof_bytes = match proof_result.serialize_proof() {
        Ok(b) => b,
        Err(e) => {
            throw_java_exception(&mut env, e);
            return std::ptr::null_mut();
        }
    };

    log::info!(
        "JNI: prove() 成功. 证明大小: {} bytes",
        proof_bytes.len()
    );

    // 转换为 Java 字节数组并返回
    match vec_to_jbyte_array(&mut env, &proof_bytes) {
        Ok(arr) => arr,
        Err(e) => {
            throw_java_exception(&mut env, e);
            std::ptr::null_mut()
        }
    }
}

/// JNI: 验证零知识证明
///
/// Java 签名: native boolean verify(byte[] verifyingKey, byte[] proof, byte[] publicInputs)
///
/// 使用验证密钥验证 Groth16 证明的正确性。
///
/// # 参数
/// - `env`: JNI 环境（自动传入）
/// - `_class`: Java 类引用（自动传入）
/// - `verifying_key_bytes`: 序列化的 VerifyingKey
/// - `proof_bytes`: 序列化的 Groth16 证明
/// - `public_inputs_bytes`: 序列化的公共输入
///
/// # 返回值
/// boolean - true 表示证明有效，false 表示证明无效
///
/// # 异常
/// 如果验证过程出错（非证明无效），抛出 RuntimeException
///
/// # Kotlin 调用示例
/// ```kotlin
/// val isValid = ZkpNative.verify(vkBytes, proofBytes, publicInputs)
/// ```
#[no_mangle]
pub extern "system" fn Java_com_sovexis_mobile_zkp_ZkpNative_verify(
    mut env: JNIEnv,
    _class: JClass,
    verifying_key_bytes: jbyteArray,
    proof_bytes: jbyteArray,
    public_inputs_bytes: jbyteArray,
) -> jboolean {
    log::info!("JNI: verify() 被调用");

    // 解析 VerifyingKey
    let vk_data = match jbyte_array_to_vec(&mut env, verifying_key_bytes) {
        Ok(d) => d,
        Err(e) => {
            throw_java_exception(&mut env, e);
            return 0; // JNI_FALSE
        }
    };

    // 解析证明
    let proof_data = match jbyte_array_to_vec(&mut env, proof_bytes) {
        Ok(d) => d,
        Err(e) => {
            throw_java_exception(&mut env, e);
            return 0;
        }
    };

    // 解析公共输入
    let public_data = match jbyte_array_to_vec(&mut env, public_inputs_bytes) {
        Ok(d) => d,
        Err(e) => {
            throw_java_exception(&mut env, e);
            return 0;
        }
    };

    // 执行验证
    match groth16_native::verify_from_bytes(&vk_data, &proof_data, &public_data) {
        Ok(is_valid) => {
            log::info!("JNI: verify() 完成, 结果: {}", is_valid);
            if is_valid {
                1 // JNI_TRUE
            } else {
                0 // JNI_FALSE
            }
        }
        Err(e) => {
            log::error!("JNI: verify() 失败: {}", e);
            throw_java_exception(&mut env, e);
            0 // JNI_FALSE
        }
    }
}

/// JNI: 获取库版本信息
///
/// Java 签名: native String getLibVersion()
///
/// 返回 ZKP 原生库的版本信息字符串。
///
/// # 返回值
/// String - 版本信息，格式: "sovexis-zkp v0.1.0 | arkworks-rs 0.6.0 | BN254 curve"
#[no_mangle]
pub extern "system" fn Java_com_sovexis_mobile_zkp_ZkpNative_getLibVersion(
    mut env: JNIEnv,
    _class: JClass,
) -> jstring {
    log::info!("JNI: getLibVersion() 被调用");

    match string_to_jstring(&mut env, crate::BUILD_INFO) {
        Ok(s) => s,
        Err(e) => {
            throw_java_exception(&mut env, e);
            std::ptr::null_mut()
        }
    }
}

/// JNI: 清理指定参数
///
/// Java 签名: native void cleanup(String prepareResultId)
///
/// 从全局存储中移除指定 ID 的参数和证明数据，释放内存。
///
/// # 参数
/// - `prepare_result_id`: prepare 阶段返回的参数 ID
#[no_mangle]
pub extern "system" fn Java_com_sovexis_mobile_zkp_ZkpNative_cleanup(
    mut env: JNIEnv,
    _class: JClass,
    prepare_result_id: jstring,
) {
    log::info!("JNI: cleanup() 被调用");

    let id = match jstring_to_string(&mut env, prepare_result_id) {
        Ok(s) => s,
        Err(e) => {
            throw_java_exception(&mut env, e);
            return;
        }
    };

    // 从参数存储中移除
    if let Ok(mut store) = PARAMS_STORE.lock() {
        if store.remove(&id).is_some() {
            log::info!("JNI: cleanup() 已移除参数: {}", id);
        }
    }

    // 从证明存储中移除
    if let Ok(mut store) = PROOF_STORE.lock() {
        if store.remove(&id).is_some() {
            log::info!("JNI: cleanup() 已移除证明: {}", id);
        }
    }
}

// ===========================================================================
// JNI 函数签名参考
// ===========================================================================
//
// 以下是对应 Kotlin 端 (ZkpNative.kt) 的 JNI 函数签名映射:
//
// Kotlin 声明                              | JNI 函数签名
// ------------------------------------------|------------------------------------------
// external fun setup(circuitType: String)   | Java_com_sovexis_mobile_zkp_ZkpNative_setup
//   : Array<ByteArray>                      |   (JNIEnv, JClass, jstring) -> jobjectArray
//                                          |
// external fun prove(                       | Java_com_sovexis_mobile_zkp_ZkpNative_prove
//   pk: ByteArray,                          |   (JNIEnv, JClass, jbyteArray, jbyteArray,
//   privateInputs: ByteArray,               |    jbyteArray) -> jbyteArray
//   publicInputs: ByteArray                 |
// ): ByteArray                              |
//                                          |
// external fun verify(                      | Java_com_sovexis_mobile_zkp_ZkpNative_verify
//   vk: ByteArray,                          |   (JNIEnv, JClass, jbyteArray, jbyteArray,
//   proof: ByteArray,                       |    jbyteArray) -> jboolean
//   publicInputs: ByteArray                 |
// ): Boolean                                |
//                                          |
// external fun getLibVersion(): String      | Java_com_sovexis_mobile_zkp_ZkpNative_getLibVersion
//                                          |   (JNIEnv, JClass) -> jstring
//                                          |
// external fun cleanup(prepareResultId: String) | Java_com_sovexis_mobile_zkp_ZkpNative_cleanup
//                                          |   (JNIEnv, JClass, jstring) -> ()
//
// ===========================================================================
