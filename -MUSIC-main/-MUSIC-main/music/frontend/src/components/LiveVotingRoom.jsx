import React, { useEffect, useState } from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import axios from 'axios'; // axios 임포트 필요

const LiveVotingRoom = () => {
    const [stompClient, setStompClient] = useState(null);
    const [songList, setSongList] = useState([]); // 👈 초기값은 빈 배열로 시작
    const [nextSongAlert, setNextSongAlert] = useState('');

    // 1. 페이지 처음 진입 시 백엔드에서 실시간 순위 데이터 가져오기 (REST API)
    const fetchRanking = async () => {
        try {
            const response = await axios.get('http://localhost:8080/api/broadcast/ranking');
            setSongList(response.data); // 백엔드에서 가져온 데이터로 세팅
        } catch (error) {
            console.error('순위 데이터를 불러오는 중 에러 발생:', error);
        }
    };

    useEffect(() => {
        fetchRanking(); // 최초 데이터 로드

        // 2. 웹소켓(STOMP) 연결 및 실시간 갱신 구독
        const socket = new SockJS('http://localhost:8080/ws-stomp');
        const client = new Client({
            webSocketFactory: () => socket,
            onConnect: () => {
                console.log('WebSocket 연결 성공!');

                // "다음 곡" 확정 알림 구독
                client.subscribe('/topic/broadcast/next-song', (message) => {
                    setNextSongAlert(message.body);
                    setTimeout(() => setNextSongAlert(''), 5000);
                });

                // 누군가 투표했을 때 실시간으로 갱신되는 순위 데이터 구독
                client.subscribe('/topic/broadcast/ranking', (message) => {
                    const updatedRanking = JSON.parse(message.body);
                    setSongList(updatedRanking); // 실시간 순위로 업데이트!
                });
            },
        });

        client.activate();
        setStompClient(client);

        return () => {
            if (client) client.deactivate();
        };
    }, []);

    const handleSelectNextSong = () => {
        if (stompClient && stompClient.connected) {
            stompClient.publish({ destination: '/app/broadcast/next-song', body: JSON.stringify({}) });
        }
    };

    const handleVote = (songTitle) => {
        if (stompClient && stompClient.connected) {
            stompClient.publish({ destination: '/app/broadcast/vote', body: songTitle });
        }
    };

    return (
        <div style={{ padding: '30px', maxWidth: '600px', margin: '0 auto' }}>
            {nextSongAlert && (
                <div style={{ position: 'fixed', top: '20px', left: '50%', transform: 'translateX(-50%)', background: '#ff4081', color: '#fff', padding: '15px 30px', borderRadius: '10px', fontSize: '22px', fontWeight: 'bold', zIndex: 1000 }}>
                    📢 {nextSongAlert}
                </div>
            )}

            <h2>🎧 실시간 신청곡 & 투표 순위</h2>

            <div style={{ background: '#f9f9f9', padding: '20px', borderRadius: '8px', marginTop: '20px' }}>
                {songList.length === 0 ? (
                    <p style={{ textAlign: 'center', color: '#888' }}>현재 신청된 곡이 없습니다.</p>
                ) : (
                    songList.map((song, index) => (
                        <div key={index} style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', padding: '12px 15px', margin: '8px 0', background: '#fff', borderRadius: '6px', boxShadow: '0 2px 4px rgba(0,0,0,0.05)' }}>
                            <div style={{ fontSize: '18px', fontWeight: 'bold' }}>
                                {index + 1}위 &nbsp; {song.songTitle}
                            </div>
                            <div style={{ display: 'flex', alignItems: 'center', gap: '15px' }}>
                                <span style={{ color: '#666' }}>👍 {song.voteCount}</span>
                                <button onClick={() => handleVote(song.songTitle)} style={{ background: '#3f51b5', color: '#fff', border: 'none', padding: '6px 12px', borderRadius: '4px', cursor: 'pointer' }}>
                                    투표하기
                                </button>
                            </div>
                        </div>
                    ))
                )}
            </div>

            <div style={{ marginTop: '30px', padding: '15px', background: '#e8f5e9', borderRadius: '8px', textAlign: 'center' }}>
                <h3>방송자 제어판</h3>
                <button onClick={handleSelectNextSong} style={{ background: '#2e7d32', color: '#fff', border: 'none', padding: '12px 24px', fontSize: '16px', fontWeight: 'bold', borderRadius: '6px', cursor: 'pointer' }}>
                    [다음 곡으로 선택] (1위 곡 자동 확정)
                </button>
            </div>
        </div>
    );
};

export default LiveVotingRoom;