// 差分同步管理模块

const deltaSyncManager = {
    // 块大小（与后端一致，4MB）
    CHUNK_SIZE: 4 * 1024 * 1024,

    /**
     * 执行差分同步更新
     * @param {string} fileId - 文件ID
     * @param {File} newFile - 新的文件对象
     * @returns {Promise<Object>} 更新结果
     */
    async syncFile(fileId, newFile) {
        try {
            showAlert(`开始差分同步: ${newFile.name}...`, 'info');

            // 1. 获取服务器文件签名
            const signatures = await api.getFileSignatures(fileId);
            if (!signatures.success || !signatures.data) {
                throw new Error('获取文件签名失败');
            }

            const serverSignatures = signatures.data;
            console.log('服务器签名:', serverSignatures);

            // 2. 读取新文件并计算块哈希
            const newFileData = await readFileAsArrayBuffer(newFile);
            const newChunks = this.splitIntoChunks(newFileData);
            const newChunkHashes = await this.calculateChunkHashes(newChunks);

            console.log('新文件块哈希:', newChunkHashes);

            // 3. 比对差异，找出需要更新的块和匹配的块
            const { deltaChunks, chunkHashes } = this.findDeltaChunks(
                serverSignatures,
                newChunkHashes,
                newChunks
            );

            console.log('差异块:', deltaChunks);
            console.log('所有块哈希:', chunkHashes);

            if (deltaChunks.length === 0 && chunkHashes.length === newChunkHashes.length) {
                // 检查是否所有块都匹配（文件可能无变化）
                let allMatched = true;
                for (let i = 0; i < newChunkHashes.length; i++) {
                    const serverHash = serverSignatures.find(s => s.chunkIndex === i)?.hash;
                    if (serverHash !== newChunkHashes[i].hash) {
                        allMatched = false;
                        break;
                    }
                }
                if (allMatched && newChunkHashes.length === serverSignatures.length) {
                    showAlert('文件无变化，无需更新', 'info');
                    return { success: true, message: '文件无变化' };
                }
            }

            // 4. 准备差分数据
            // deltaChunks: 需要上传的变更块数据（Base64编码）
            // chunkHashes: 所有块的哈希信息（用于后端匹配）
            const deltaData = {
                deltaChunks: {},
                chunkHashes: {}
            };
            
            // 确保使用字符串键（JSON要求）
            for (const delta of deltaChunks) {
                const chunkData = new Uint8Array(newFileData.slice(
                    delta.index * this.CHUNK_SIZE,
                    Math.min((delta.index + 1) * this.CHUNK_SIZE, newFileData.byteLength)
                ));
                // 转换为Base64字符串
                const base64 = this.arrayBufferToBase64(chunkData);
                deltaData.deltaChunks[String(delta.index)] = base64;
            }
            
            // 发送所有块的哈希信息（用于后端匹配和复用）
            // 确保使用字符串键
            for (const chunkHash of chunkHashes) {
                deltaData.chunkHashes[String(chunkHash.index)] = chunkHash.hash;
            }

            console.log('发送差分数据:', {
                deltaChunksCount: Object.keys(deltaData.deltaChunks).length,
                chunkHashesCount: Object.keys(deltaData.chunkHashes).length,
                deltaChunksKeys: Object.keys(deltaData.deltaChunks),
                chunkHashesKeys: Object.keys(deltaData.chunkHashes)
            });

            // 5. 应用差分更新
            const result = await api.applyDelta(fileId, deltaData);

            if (result.success) {
                showAlert(
                    `差分同步成功: ${newFile.name} (更新了 ${deltaChunks.length} 个块)`,
                    'success'
                );
                return result;
            } else {
                throw new Error(result.message || '差分同步失败');
            }
        } catch (error) {
            console.error('差分同步错误:', error);
            showAlert(`差分同步失败: ${error.message}`, 'error');
            throw error;
        }
    },

    /**
     * 将文件数据切分为块
     * @param {ArrayBuffer} data - 文件数据
     * @returns {Array<ArrayBuffer>} 块数组
     */
    splitIntoChunks(data) {
        const chunks = [];
        const totalChunks = Math.ceil(data.byteLength / this.CHUNK_SIZE);

        for (let i = 0; i < totalChunks; i++) {
            const start = i * this.CHUNK_SIZE;
            const end = Math.min(start + this.CHUNK_SIZE, data.byteLength);
            chunks.push(data.slice(start, end));
        }

        return chunks;
    },

    /**
     * 计算所有块的哈希值
     * @param {Array<ArrayBuffer>} chunks - 块数组
     * @returns {Promise<Array<{index: number, hash: string, size: number}>>} 哈希数组
     */
    async calculateChunkHashes(chunks) {
        const hashes = [];

        for (let i = 0; i < chunks.length; i++) {
            const hash = await hashCalculator.calculateArrayBufferHash(chunks[i]);
            hashes.push({
                index: i,
                hash: hash,
                size: chunks[i].byteLength
            });
        }

        return hashes;
    },

    /**
     * 找出需要更新的块和所有块的哈希信息
     * @param {Array} serverSignatures - 服务器块签名
     * @param {Array} newChunkHashes - 新文件块哈希
     * @param {Array<ArrayBuffer>} newChunks - 新文件块数据
     * @returns {{deltaChunks: Array, chunkHashes: Array}} 需要更新的块和所有块哈希
     */
    findDeltaChunks(serverSignatures, newChunkHashes, newChunks) {
        const deltaChunks = [];
        const chunkHashes = [];
        
        // 构建服务器哈希集合（用于快速查找）
        const serverHashSet = new Set();
        for (const sig of serverSignatures) {
            serverHashSet.add(sig.hash);
        }

        // 比对每个块
        for (const newChunk of newChunkHashes) {
            // 记录所有块的哈希信息
            chunkHashes.push({
                index: newChunk.index,
                hash: newChunk.hash
            });
            
            // 如果块哈希不在服务器中，需要上传
            if (!serverHashSet.has(newChunk.hash)) {
                deltaChunks.push({
                    index: newChunk.index,
                    hash: newChunk.hash,
                    size: newChunk.size
                });
            }
        }

        return { deltaChunks, chunkHashes };
    },

    /**
     * 将ArrayBuffer转换为Base64字符串
     * @param {Uint8Array} bytes - 字节数组
     * @returns {string} Base64字符串
     */
    arrayBufferToBase64(bytes) {
        let binary = '';
        const len = bytes.byteLength;
        for (let i = 0; i < len; i++) {
            binary += String.fromCharCode(bytes[i]);
        }
        return btoa(binary);
    }
};

