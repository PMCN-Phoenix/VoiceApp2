const WebSocket = require('ws');

// 创建 WebSocket 服务器，监听 8080 端口
const wss = new WebSocket.Server({ port: 8080 });

console.log('Mock ASR 服务器已启动，监听端口 8080');

wss.on('connection', (ws) => {
    console.log('客户端已连接');

    // 每次收到消息时触发
    ws.on('message', (data) => {
        const text = data.toString();
        console.log('收到消息:', text.substring(0, 100) + '...');  // 只打印前100个字符，避免刷屏

        try {
            const msg = JSON.parse(text);

            // 根据消息类型做出不同响应
            if (msg.type === 'start') {
                console.log('  → 语音段开始');
            } else if (msg.type === 'data') {
                // 数据帧太多，不逐条打印
            } else if (msg.type === 'end') {
                console.log('  → 语音段结束，返回模拟结果');
                // 模拟 ASR 返回的转写结果
                const result = JSON.stringify({
                    type: 'result',
                    text: '这是一段模拟的识别结果',
                    is_final: true,
                    confidence: 0.95
                });
                ws.send(result);
                console.log('  → 已发送: ' + result);
            }
        } catch (e) {
            console.error('  → JSON 解析失败:', e.message);
        }
    });

    ws.on('close', () => {
        console.log('客户端已断开');
    });
});