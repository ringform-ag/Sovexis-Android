// ===========================================================================
// Sovexis ZKP Playground - Rust 库入口
// ===========================================================================
//
// [AI-GENERATED]
// 生成时间: 2026-05-20
// 实现状态: FRAMEWORK - 框架文件，待人工完善
// 人工补充: 具体电路模块的注册与集成
//
// 本文件是 sovexis_zkp Rust 库的入口点。
// 负责模块组织和 JNI 函数导出。
//
// 模块结构:
//   lib.rs              - 库入口，模块导出
//   groth16_native.rs   - Groth16 原生实现（prove/verify/setup）
//   jni_bridge.rs       - JNI 桥接层（Java <-> Rust 类型转换）
//
// 编译产物:
//   - Android: libsovexis_zkp.so
//   - 宿主机: libsovexis_zkp.dylib (macOS) / libsovexis_zkp.so (Linux)
//
// ===========================================================================

// 禁用 Rust 标准输出，避免在 Android 环境中产生未定义行为
// Android 的 logcat 日志通过 android_logger 或 log crate 输出
#![allow(clippy::all)]

// ===========================================================================
// 模块声明
// ===========================================================================

/// Groth16 原生实现模块
///
/// 包含基于 arkworks-rs 的 Groth16 zkSNARK 核心功能:
/// - `setup()`: 可信设置（Trusted Setup），生成 proving key 和 verifying key
/// - `prove()`: 证明生成，使用 proving key 和见证生成零知识证明
/// - `verify()`: 证明验证，使用 verifying key 和公共输入验证证明
///
/// 使用的椭圆曲线: BN254 (Ethereum 兼容的配对友好曲线)
/// 使用的配对引擎: ark_bn254::Bn254
pub mod groth16_native;

/// JNI 桥接模块
///
/// 实现 Java_com_sovexis_mobile_zkp_ZkpNative_* 系列 JNI 函数。
/// 负责:
/// - Java 字节数组 (jbyteArray) 到 Rust Vec<u8> 的转换
/// - Java 字符串 (jstring) 到 Rust String 的转换
/// - Rust 错误类型到 Java 异常的转换
/// - JNI 环境的生命周期管理
///
/// JNI 函数命名规范:
///   Java_<包名>_<类名>_<方法名>
///   包名中的点号替换为下划线
pub mod jni_bridge;

// ===========================================================================
// 全局类型别名
// ===========================================================================

/// 配对友好曲线类型别名
///
/// 使用 BN254 曲线，这是 Ethereum 生态广泛使用的配对友好曲线。
/// BN254 提供 128-bit 安全级别，支持高效的配对运算。
///
/// 为什么选择 BN254:
/// 1. Ethereum 兼容 - 可与智能合约验证集成
/// 2. 成熟稳定 - 经过大量生产环境验证
/// 3. 工具链完善 - arkworks-rs 对 BN254 有最佳支持
/// 4. 证明体积小 - Groth16 + BN254 的证明仅 ~200 字节
pub type CurvePairing = ark_bn254::Bn254;

/// 标量场类型别名
///
/// BN254 的标量场 Fr，用于表示见证值和随机挑战。
/// 场的阶: 21888242871839275222246405745257275088548364400416034343698204186575808495617
pub type ScalarField = <CurvePairing as ark_ec::pairing::Pairing>::ScalarField;

/// G1 群元素类型别名
///
/// BN254 的 G1 群，用于证明中的群元素。
pub type G1Projective = <CurvePairing as ark_ec::pairing::Pairing>::G1;

/// G2 群元素类型别名
///
/// BN254 的 G2 群，用于验证密钥中的群元素。
pub type G2Projective = <CurvePairing as ark_ec::pairing::Pairing>::G2;

// ===========================================================================
// 错误类型定义
// ===========================================================================

/// Sovexis ZKP 库统一错误类型
///
/// 封装所有可能的错误场景，便于 JNI 层统一处理并转换为 Java 异常。
#[derive(Debug, thiserror::Error)]
pub enum ZkpError {
    /// 电路综合错误 (ark_relations::SynthesisError)
    #[error("电路综合失败: {0}")]
    CircuitSynthesis(String),

    /// 证明生成错误
    #[error("证明生成失败: {0}")]
    ProveFailed(String),

    /// 证明验证错误
    #[error("证明验证失败: {0}")]
    VerifyFailed(String),

    /// 序列化/反序列化错误
    #[error("序列化错误: {0}")]
    Serialization(String),

    /// 参数解析错误（输入格式不正确）
    #[error("参数解析错误: {0}")]
    InvalidInput(String),

    /// JNI 调用错误
    #[error("JNI 错误: {0}")]
    JniError(String),

    /// 内部错误（未预期的运行时错误）
    #[error("内部错误: {0}")]
    Internal(String),
}

/// 统一结果类型别名
pub type ZkpResult<T> = Result<T, ZkpError>;

// ===========================================================================
// JNI 导出
// ===========================================================================

/// JNI 函数导出
///
/// 这些函数通过 JNI 桥接层暴露给 Android Kotlin 代码。
/// Kotlin 端通过 `System.loadLibrary("sovexis_zkp")` 加载本库后，
/// 即可调用这些 native 方法。
///
/// 导出的 JNI 函数:
/// - Java_com_sovexis_mobile_zkp_ZkpNative_setup
/// - Java_com_sovexis_mobile_zkp_ZkpNative_prove
/// - Java_com_sovexis_mobile_zkp_ZkpNative_verify
pub use jni_bridge::*;

// ===========================================================================
// 库版本信息
// ===========================================================================

/// 库版本号
pub const LIB_VERSION: &str = env!("CARGO_PKG_VERSION");

/// 库构建信息
pub const BUILD_INFO: &str = concat!(
    "sovexis-zkp v",
    env!("CARGO_PKG_VERSION"),
    " | arkworks-rs 0.6.0 | BN254 curve"
);

// ===========================================================================
// 初始化日志
// ===========================================================================

/// 库初始化函数
///
/// 在 JNI_OnLoad 中调用，初始化日志系统。
/// 注意: Android 环境下应使用 android_logger 替代 env_logger。
#[allow(dead_code)]
fn init_logging() {
    // 在 Android 环境下，日志通过 logcat 输出
    // 在宿主机环境下，使用 env_logger 输出到标准错误
    #[cfg(not(target_os = "android"))]
    {
        let _ = env_logger::builder()
            .filter_level(log::LevelFilter::Info)
            .try_init();
    }
    log::info!("Sovexis ZKP library initialized. {}", BUILD_INFO);
}

// ===========================================================================
// JNI 生命周期管理
// ===========================================================================

/// JNI_OnLoad - 动态库加载时由 JVM 自动调用
///
/// 在 Android 中，当 Kotlin 代码调用 `System.loadLibrary("sovexis_zkp")` 时，
/// JVM 会自动调用此函数。可用于:
/// 1. 初始化日志系统
/// 2. 注册 JNI 方法（可选，也可通过命名约定自动匹配）
/// 3. 初始化全局状态
///
/// # 返回值
/// 返回 JNI 版本号 `JNI_VERSION_1_6`，表示使用 JNI 1.6 规范。
///
/// # Safety
/// 此函数由 JVM 调用，vm 参数保证非空且有效。
#[no_mangle]
pub extern "system" fn JNI_OnLoad(
    vm: jni::JavaVM,
    _reserved: *mut std::ffi::c_void,
) -> jni::sys::jint {
    init_logging();
    log::info!("JNI_OnLoad: sovexis_zkp library loaded successfully");

    // 返回 JNI 版本 1.6
    jni::sys::JNI_VERSION_1_6
}

/// JNI_OnUnload - 动态库卸载时由 JVM 自动调用
///
/// 用于清理全局资源。
///
/// # Safety
/// 此函数由 JVM 调用，vm 参数保证非空且有效。
#[no_mangle]
pub extern "system" fn JNI_OnUnload(
    _vm: jni::JavaVM,
    _reserved: *mut std::ffi::c_void,
) {
    log::info!("JNI_OnUnload: sovexis_zkp library unloading");
}
