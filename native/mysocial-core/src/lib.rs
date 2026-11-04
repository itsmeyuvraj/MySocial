pub mod fec;
pub mod pow;

use jni::objects::{JClass, JObjectArray};
use jni::sys::{jboolean, jbyteArray, jint, jlong, JNI_TRUE};
use jni::JNIEnv;

/// Confirms the native library was loaded successfully.
#[no_mangle]
pub extern "system" fn Java_com_mysocial_mesh_nativebridge_NativeMeshCore_isNativeLoaded(
    _env: JNIEnv,
    _class: JClass,
) -> jboolean {
    JNI_TRUE
}

/// Mines a Hashcash Proof-of-Work nonce using hardware-accelerated SHA-256.
#[no_mangle]
pub extern "system" fn Java_com_mysocial_mesh_nativebridge_NativeMeshCore_mineHashcash(
    env: JNIEnv,
    _class: JClass,
    challenge_bytes: jbyteArray,
    difficulty_bits: jint,
) -> jlong {
    let challenge = match unsafe { env.convert_byte_array(&jni::objects::JByteArray::from_raw(challenge_bytes)) } {
        Ok(bytes) => bytes,
        Err(_) => return 0,
    };

    let difficulty = if difficulty_bits < 0 { 0 } else { difficulty_bits as u32 };
    let nonce = pow::mine_hashcash(&challenge, difficulty);

    nonce as jlong
}

/// Verifies whether a mined nonce satisfies the Hashcash difficulty requirement.
#[no_mangle]
pub extern "system" fn Java_com_mysocial_mesh_nativebridge_NativeMeshCore_verifyHashcash(
    env: JNIEnv,
    _class: JClass,
    challenge_bytes: jbyteArray,
    nonce: jlong,
    difficulty_bits: jint,
) -> jboolean {
    let challenge = match unsafe { env.convert_byte_array(&jni::objects::JByteArray::from_raw(challenge_bytes)) } {
        Ok(bytes) => bytes,
        Err(_) => return 0,
    };

    let difficulty = if difficulty_bits < 0 { 0 } else { difficulty_bits as u32 };
    let nonce_u64 = nonce as u64;

    if pow::verify_hashcash(&challenge, nonce_u64, difficulty) {
        JNI_TRUE
    } else {
        0
    }
}

/// Computes an XOR parity block for a 2D array of byte shards: byte[][].
#[no_mangle]
pub extern "system" fn Java_com_mysocial_mesh_nativebridge_NativeMeshCore_computeXorParity(
    mut env: JNIEnv,
    _class: JClass,
    shards_array: JObjectArray,
) -> jbyteArray {
    let len = match env.get_array_length(&shards_array) {
        Ok(l) => l,
        Err(_) => return std::ptr::null_mut(),
    };

    let mut byte_shards: Vec<Vec<u8>> = Vec::with_capacity(len as usize);
    for i in 0..len {
        let obj = match env.get_object_array_element(&shards_array, i) {
            Ok(o) => o,
            Err(_) => return std::ptr::null_mut(),
        };
        let barray = jni::objects::JByteArray::from(obj);
        match env.convert_byte_array(&barray) {
            Ok(bytes) => byte_shards.push(bytes),
            Err(_) => return std::ptr::null_mut(),
        }
    }

    let shard_slices: Vec<&[u8]> = byte_shards.iter().map(|s| s.as_slice()).collect();
    let parity = fec::compute_xor_parity(&shard_slices);

    match env.byte_array_from_slice(&parity) {
        Ok(arr) => arr.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}

/// Reconstructs a missing shard from present shards and the parity block.
#[no_mangle]
pub extern "system" fn Java_com_mysocial_mesh_nativebridge_NativeMeshCore_reconstructMissingShard(
    mut env: JNIEnv,
    _class: JClass,
    present_shards_array: JObjectArray,
    parity_bytes: jbyteArray,
) -> jbyteArray {
    let len = match env.get_array_length(&present_shards_array) {
        Ok(l) => l,
        Err(_) => return std::ptr::null_mut(),
    };

    let mut byte_shards: Vec<Vec<u8>> = Vec::with_capacity(len as usize);
    for i in 0..len {
        let obj = match env.get_object_array_element(&present_shards_array, i) {
            Ok(o) => o,
            Err(_) => return std::ptr::null_mut(),
        };
        let barray = jni::objects::JByteArray::from(obj);
        match env.convert_byte_array(&barray) {
            Ok(bytes) => byte_shards.push(bytes),
            Err(_) => return std::ptr::null_mut(),
        }
    }

    let parity = match unsafe { env.convert_byte_array(&jni::objects::JByteArray::from_raw(parity_bytes)) } {
        Ok(bytes) => bytes,
        Err(_) => return std::ptr::null_mut(),
    };

    let shard_slices: Vec<&[u8]> = byte_shards.iter().map(|s| s.as_slice()).collect();
    let recovered = fec::reconstruct_missing_shard(&shard_slices, &parity);

    match env.byte_array_from_slice(&recovered) {
        Ok(arr) => arr.into_raw(),
        Err(_) => std::ptr::null_mut(),
    }
}
