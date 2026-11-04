/// Computes an XOR parity block across multiple equal-sized data shards.
/// Any single missing shard can be reconstructed by XORing the remaining shards with this parity block.
pub fn compute_xor_parity(shards: &[&[u8]]) -> Vec<u8> {
    if shards.is_empty() {
        return Vec::new();
    }

    let max_len = shards.iter().map(|s| s.len()).max().unwrap_or(0);
    let mut parity = vec![0u8; max_len];

    for shard in shards {
        for (i, byte) in shard.iter().enumerate() {
            parity[i] ^= *byte;
        }
    }

    parity
}

/// Reconstructs one missing shard given all other present shards and the parity shard.
pub fn reconstruct_missing_shard(present_shards: &[&[u8]], parity: &[u8]) -> Vec<u8> {
    let mut reconstructed = parity.to_vec();

    for shard in present_shards {
        for (i, byte) in shard.iter().enumerate() {
            if i < reconstructed.len() {
                reconstructed[i] ^= *byte;
            }
        }
    }

    reconstructed
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn test_fec_parity_reconstruction() {
        let chunk1 = b"Hello, Mesh World!".to_vec();
        let chunk2 = b"Fast Bluetooth P2P".to_vec();
        let chunk3 = b"Zero Server Gossip".to_vec();

        let shards = vec![chunk1.as_slice(), chunk2.as_slice(), chunk3.as_slice()];
        let parity = compute_xor_parity(&shards);

        // Simulate dropping chunk2
        let present = vec![chunk1.as_slice(), chunk3.as_slice()];
        let recovered = reconstruct_missing_shard(&present, &parity);

        assert_eq!(recovered, chunk2);
    }
}

