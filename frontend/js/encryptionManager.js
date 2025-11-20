// 加密管理模块（使用Web Crypto API）

const encryptionManager = {
    // 支持的加密算法
    ALGORITHMS: {
        'AES-256-GCM': { name: 'AES-GCM', length: 256 },
        'AES-256-CBC': { name: 'AES-CBC', length: 256 }
    },

    // 密钥派生算法
    KEY_DERIVATION: {
        'PBKDF2': 'PBKDF2'
    },

    /**
     * 加密文件
     * @param {File} file - 要加密的文件
     * @param {string} password - 加密密码
     * @param {Object} options - 加密选项
     * @returns {Promise<{encryptedFile: Blob, metadata: Object}>} 加密后的文件和元数据
     */
    async encryptFile(file, password, options = {}) {
        const {
            algorithm = 'AES-256-GCM',
            keyDerivation = 'PBKDF2',
            iterations = 100000,
            convergent = false
        } = options;

        try {
            // 1. 读取文件数据
            const fileData = await readFileAsArrayBuffer(file);

            // 2. 生成盐值（收敛加密使用固定盐值）
            const salt = convergent 
                ? await this.generateFixedSalt(file.name)
                : crypto.getRandomValues(new Uint8Array(16));

            // 3. 派生加密密钥
            const keyMaterial = await this.deriveKeyMaterial(password, salt, iterations, keyDerivation);
            const cryptoKey = await this.deriveCryptoKey(keyMaterial, algorithm);

            // 4. 生成IV（初始化向量）
            const iv = crypto.getRandomValues(new Uint8Array(12)); // GCM需要12字节IV

            // 5. 加密数据
            const encryptedData = await crypto.subtle.encrypt(
                {
                    name: this.ALGORITHMS[algorithm].name,
                    iv: iv
                },
                cryptoKey,
                fileData
            );

            // 6. 计算原始文件哈希（用于完整性校验）
            const originalHash = await hashCalculator.calculateArrayBufferHash(fileData);

            // 7. 创建加密文件Blob
            const encryptedBlob = new Blob([encryptedData], { type: 'application/octet-stream' });

            // 8. 构建元数据
            const metadata = {
                fileName: file.name,
                algorithm: algorithm,
                keyDerivation: keyDerivation,
                salt: this.arrayBufferToBase64(salt),
                iv: this.arrayBufferToBase64(iv),
                iterations: iterations,
                convergent: convergent,
                originalSize: file.size,
                encryptedSize: encryptedData.byteLength,
                originalHash: originalHash
            };

            return {
                encryptedFile: encryptedBlob,
                metadata: metadata
            };
        } catch (error) {
            console.error('加密文件错误:', error);
            throw new Error(`加密失败: ${error.message}`);
        }
    },

    /**
     * 派生密钥材料
     * @param {string} password - 密码
     * @param {Uint8Array} salt - 盐值
     * @param {number} iterations - 迭代次数
     * @param {string} keyDerivation - 密钥派生算法
     * @returns {Promise<CryptoKey>} 密钥材料
     */
    async deriveKeyMaterial(password, salt, iterations, keyDerivation) {
        const encoder = new TextEncoder();
        const passwordKey = await crypto.subtle.importKey(
            'raw',
            encoder.encode(password),
            'PBKDF2',
            false,
            ['deriveBits', 'deriveKey']
        );

        return crypto.subtle.deriveKey(
            {
                name: 'PBKDF2',
                salt: salt,
                iterations: iterations,
                hash: 'SHA-256'
            },
            passwordKey,
            { name: 'AES-GCM', length: 256 },
            false,
            ['encrypt', 'decrypt']
        );
    },

    /**
     * 派生加密密钥
     * @param {CryptoKey} keyMaterial - 密钥材料
     * @param {string} algorithm - 加密算法
     * @returns {Promise<CryptoKey>} 加密密钥
     */
    async deriveCryptoKey(keyMaterial, algorithm) {
        // 对于AES-GCM和AES-CBC，直接使用keyMaterial
        return keyMaterial;
    },

    /**
     * 生成固定盐值（用于收敛加密）
     * @param {string} fileName - 文件名
     * @returns {Promise<Uint8Array>} 固定盐值
     */
    async generateFixedSalt(fileName) {
        // 使用文件名的哈希作为固定盐值
        const encoder = new TextEncoder();
        const data = encoder.encode(fileName);
        const hashBuffer = await crypto.subtle.digest('SHA-256', data);
        return new Uint8Array(hashBuffer.slice(0, 16)); // 取前16字节
    },

    /**
     * 将ArrayBuffer转换为Base64字符串
     * @param {ArrayBuffer|Uint8Array} buffer - 缓冲区
     * @returns {string} Base64字符串
     */
    arrayBufferToBase64(buffer) {
        const bytes = buffer instanceof Uint8Array ? buffer : new Uint8Array(buffer);
        let binary = '';
        for (let i = 0; i < bytes.byteLength; i++) {
            binary += String.fromCharCode(bytes[i]);
        }
        return btoa(binary);
    },

    /**
     * 检查收敛加密文件是否可以秒传
     * @param {string} originalHash - 原始文件哈希
     * @returns {Promise<boolean>} 是否可以秒传
     */
    async checkConvergentQuickUpload(originalHash) {
        try {
            const response = await api.checkConvergentQuickUpload(originalHash);
            return response.success && response.data?.canQuickUpload === true;
        } catch (error) {
            console.error('检查收敛加密秒传失败:', error);
            return false;
        }
    }
};

