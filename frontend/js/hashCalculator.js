// 哈希计算工具（使用Web Crypto API）

class HashCalculator {
    constructor() {
        this.algorithm = 'SHA-256';
    }

    // 计算文件的SHA-256哈希值
    async calculateFileHash(file) {
        const arrayBuffer = await readFileAsArrayBuffer(file);
        return this.calculateArrayBufferHash(arrayBuffer);
    }

    // 计算ArrayBuffer的哈希值
    async calculateArrayBufferHash(arrayBuffer) {
        const hashBuffer = await crypto.subtle.digest(this.algorithm, arrayBuffer);
        const hashArray = Array.from(new Uint8Array(hashBuffer));
        const hashHex = hashArray.map(b => b.toString(16).padStart(2, '0')).join('');
        return hashHex;
    }

    // 计算文件块的哈希值（用于块级去重）
    async calculateChunkHash(chunk) {
        if (chunk instanceof ArrayBuffer) {
            return this.calculateArrayBufferHash(chunk);
        } else if (chunk instanceof Blob) {
            const arrayBuffer = await readFileAsArrayBuffer(chunk);
            return this.calculateArrayBufferHash(arrayBuffer);
        } else {
            throw new Error('不支持的块类型');
        }
    }

    // 将文件切分为块并计算每个块的哈希
    async calculateChunkHashes(file, chunkSize = CONFIG.UPLOAD.BLOCK_SIZE) {
        const chunks = [];
        const hashes = [];
        const totalChunks = Math.ceil(file.size / chunkSize);

        for (let i = 0; i < totalChunks; i++) {
            const start = i * chunkSize;
            const end = Math.min(start + chunkSize, file.size);
            const chunk = file.slice(start, end);
            
            chunks.push(chunk);
            const hash = await this.calculateChunkHash(chunk);
            hashes.push({
                index: i,
                hash: hash,
                size: chunk.size,
                start: start,
                end: end
            });
        }

        return {
            chunks,
            hashes,
            totalChunks
        };
    }

    // 计算文件完整哈希（用于文件级去重/秒传）
    async calculateFullFileHash(file) {
        return this.calculateFileHash(file);
    }

    // 比较两个哈希值
    compareHashes(hash1, hash2) {
        return hash1.toLowerCase() === hash2.toLowerCase();
    }
}

// 创建全局哈希计算器实例
const hashCalculator = new HashCalculator();

