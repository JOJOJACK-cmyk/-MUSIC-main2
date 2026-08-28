import React, { useEffect, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';

const BroadcastLive = () => {
    const [nextSongAlert, setNextSongAlert] = useState('');
    const [stompClient, setStompClient] = useState(null);

    useEffect(() => {
        // 1. WebSocket 연결 설정 (백엔드 엔드포인트에 맞게 수정)
        const socket = new SockJS('http://localhost:8080/ws-stomp');
        const client = new Client({
            webSocketFactory: () => socket,
            onConnect: () => {
                console.log('WebSocket 연결 성공!');

                // 2. [시청자 화면] "다음 곡" 알림 토픽 구독
                client.subscribe('/topic/broadcast/next-song', (message) => {
                    // 백엔드에서 보낸 "다음 곡: Ditto" 메시지 수신
                    setNextSongAlert(message.body);

                    // 3초 뒤 알림 자동 닫기 등 처리 가능
                    setTimeout(() => {
                        setNextSongAlert('');
                    }, 5000);
                });
            },
        });

        client.activate();
        setStompClient(client);

        return () => {
            if (client) client.deactivate();
        };
    }, []);

    // 3. [방송자용] [다음 곡으로 선택] 버튼 클릭 시 실행하는 함수
    const handleSelectNextSong = () => {
        if (stompClient && stompClient.connected) {
            stompClient.publish({
                destination: '/app/broadcast/next-song',
                body: JSON.stringify({}), // 필요시 데이터 담기
            });
        }
    };

    // 4. [시청자용] 특정 곡에 투표(좋아요)하기 버튼 클릭 시 실행하는 함수
    const handleVoteSong = (songTitle) => {
        if (stompClient && stompClient.connected) {
            stompClient.publish({
                destination: '/app/broadcast/vote',
                body: songTitle, // 예: "Ditto"
            });
        }
    };

    return (
        <div style={{ padding: '20px' }}>
            <h2>🎵 실시간 음악 방송</h2>

            {/* 전체 시청자 화면에 뜨는 '다음 곡' 팝업/배너 UI */}
            {nextSongAlert && (
                <div style={{ background: '#ffeb3b', padding: '15px', fontSize: '20px', fontWeight: 'bold', textAlign: 'center' }}>
                    📢 {nextSongAlert}
                </div>
            )}

            {/* 방송자용 버튼 */}
            <div style={{ marginTop: '20px' }}>
                <button onClick={handleSelectNextSong} style={{ background: '#ff4081', color: '#fff', padding: '10px 20px' }}>
                    [다음 곡으로 선택] (방송자용)
                </button>
            </div>

            {/* 시청자용 투표 버튼 예시 */}
            <div style={{ marginTop: '20px' }}>
                <button onClick={() => handleVoteSong('Ditto')}>'Ditto' 좋아요 투표하기</button>
            </div>
        </div>
    );
};

export default BroadcastLive;