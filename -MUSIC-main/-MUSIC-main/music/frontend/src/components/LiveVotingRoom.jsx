import React, {
  useEffect,
  useState,
} from 'react';
import { Client } from '@stomp/stompjs';
import SockJS from 'sockjs-client';
import axios from 'axios';

const LiveVotingRoom = ({
  broadcastId,
  isBroadcaster,
}) => {
  const [stompClient, setStompClient] =
    useState(null);

  const [songList, setSongList] =
    useState([]);

  const [nextSongAlert, setNextSongAlert] =
    useState('');

  const [requestSong, setRequestSong] =
    useState('');

  // 현재 방송의 순위 조회
  const fetchRanking = async () => {
    if (!broadcastId) {
      return;
    }

    try {
      const response = await axios.get(
        `http://localhost:8080/api/broadcast/${broadcastId}/ranking`
      );

      setSongList(response.data);
    } catch (error) {
      console.error(
        '순위 데이터를 불러오는 중 에러 발생:',
        error
      );
    }
  };

  // WebSocket 연결
  useEffect(() => {
    if (!broadcastId) {
      return;
    }

    // 최초 순위 조회
    fetchRanking();

    const client = new Client({
      webSocketFactory: () =>
        new SockJS(
          'http://localhost:8080/ws-stomp'
        ),

      reconnectDelay: 5000,

      onConnect: () => {
        console.log(
          `방송 ${broadcastId} WebSocket 연결 성공!`
        );

        // 다음 곡 알림 구독
        client.subscribe(
          `/topic/broadcast/${broadcastId}/next-song`,
          (message) => {
            setNextSongAlert(message.body);

            setTimeout(() => {
              setNextSongAlert('');
            }, 5000);
          }
        );

        // 실시간 순위 구독
        client.subscribe(
          `/topic/broadcast/${broadcastId}/ranking`,
          (message) => {
            try {
              const updatedRanking =
                JSON.parse(message.body);

              setSongList(updatedRanking);
            } catch (error) {
              console.error(
                '실시간 순위 데이터 파싱 실패:',
                error
              );
            }
          }
        );
      },

      onStompError: (frame) => {
        console.error(
          'STOMP 오류:',
          frame.headers['message']
        );
      },

      onWebSocketError: (error) => {
        console.error(
          'WebSocket 오류:',
          error
        );
      },
    });

    client.activate();
    setStompClient(client);

    return () => {
      client.deactivate();
      setStompClient(null);
    };
  }, [broadcastId]);

  // 신청곡 등록
  const handleRequestSong = () => {
    const songTitle =
      requestSong.trim();

    if (!songTitle) {
      return;
    }

    if (
      !stompClient ||
      !stompClient.connected
    ) {
      console.warn(
        'WebSocket이 아직 연결되지 않았습니다.'
      );

      return;
    }

    stompClient.publish({
      destination:
        `/app/broadcast/${broadcastId}/vote`,
      body: songTitle,
    });

    setRequestSong('');
  };

  // 기존 곡 투표
  const handleVote = (songTitle) => {
    if (
      !stompClient ||
      !stompClient.connected
    ) {
      console.warn(
        'WebSocket이 아직 연결되지 않았습니다.'
      );

      return;
    }

    stompClient.publish({
      destination:
        `/app/broadcast/${broadcastId}/vote`,
      body: songTitle,
    });
  };

  // 1위 곡 다음 곡으로 선택
  const handleSelectNextSong = () => {
    // 프론트에서도 한 번 더 방어
    if (!isBroadcaster) {
      console.warn(
        '방송자만 다음 곡을 선택할 수 있습니다.'
      );

      return;
    }

    if (
      !stompClient ||
      !stompClient.connected
    ) {
      console.warn(
        'WebSocket이 아직 연결되지 않았습니다.'
      );

      return;
    }

    stompClient.publish({
      destination:
        `/app/broadcast/${broadcastId}/next-song`,
      body: JSON.stringify({}),
    });
  };

  return (
    <div
      style={{
        width: '100%',
        color: '#fff',
      }}
    >
      {/* 제목 */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent:
            'space-between',
          marginBottom: '20px',
        }}
      >
        <div>
          <div
            style={{
              fontSize: '13px',
              color: '#b7a9b8',
              marginBottom: '5px',
            }}
          >
            LIVE REQUEST
          </div>

          <h2
            style={{
              margin: 0,
              fontSize: '24px',
              fontWeight: '700',
              letterSpacing: '-0.5px',
            }}
          >
            신청곡 & 실시간 투표
          </h2>
        </div>

        <div
          style={{
            background:
              'rgba(255,45,120,0.12)',
            color: '#ff4d8d',
            border:
              '1px solid rgba(255,45,120,0.3)',
            padding: '6px 11px',
            borderRadius: '999px',
            fontSize: '12px',
            fontWeight: '700',
          }}
        >
          TOP {songList.length}
        </div>
      </div>

      {/* 신청곡 입력 */}
      <div
        style={{
          display: 'flex',
          gap: '10px',
          marginBottom: '18px',
        }}
      >
        <input
          type="text"
          value={requestSong}
          onChange={(e) =>
            setRequestSong(
              e.target.value
            )
          }
          onKeyDown={(e) => {
            if (e.key === 'Enter') {
              handleRequestSong();
            }
          }}
          placeholder="신청할 곡 제목을 입력하세요"
          style={{
            flex: 1,
            minWidth: 0,
            background: '#111',
            border:
              '1px solid #383838',
            color: '#fff',
            padding: '12px 14px',
            borderRadius: '9px',
            outline: 'none',
            fontSize: '14px',
          }}
        />

        <button
          onClick={
            handleRequestSong
          }
          disabled={
            !requestSong.trim()
          }
          style={{
            flexShrink: 0,
            border: 'none',
            background:
              requestSong.trim()
                ? '#ff2f7d'
                : '#333',
            color:
              requestSong.trim()
                ? '#fff'
                : '#777',
            padding: '0 20px',
            borderRadius: '9px',
            cursor:
              requestSong.trim()
                ? 'pointer'
                : 'not-allowed',
            fontWeight: '700',
            fontSize: '14px',
          }}
        >
          신청
        </button>
      </div>

      {/* 다음 곡 알림 */}
      {nextSongAlert && (
        <div
          style={{
            marginBottom: '18px',
            background:
              'rgba(255,45,120,0.12)',
            border:
              '1px solid rgba(255,45,120,0.35)',
            color: '#ff8db6',
            padding: '14px 16px',
            borderRadius: '10px',
            fontWeight: '600',
          }}
        >
          {nextSongAlert}
        </div>
      )}

      {/* 순위 */}
      <div
        style={{
          background: '#111',
          border:
            '1px solid #2d2d2d',
          borderRadius: '12px',
          overflow: 'hidden',
        }}
      >
        {songList.length === 0 ? (
          <div
            style={{
              minHeight: '150px',
              display: 'flex',
              flexDirection: 'column',
              justifyContent:
                'center',
              alignItems: 'center',
              color: '#777',
              textAlign: 'center',
            }}
          >
            <div
              style={{
                fontSize: '15px',
                color: '#aaa',
                marginBottom: '6px',
              }}
            >
              아직 신청된 곡이 없습니다
            </div>

            <div
              style={{
                fontSize: '13px',
                color: '#666',
              }}
            >
              위 입력창에서 첫 신청곡을
              등록해보세요.
            </div>
          </div>
        ) : (
          songList.map(
            (song, index) => (
              <div
                key={
                  song.songTitle ??
                  index
                }
                style={{
                  display: 'flex',
                  alignItems:
                    'center',
                  gap: '14px',
                  padding:
                    '15px 16px',
                  borderBottom:
                    index ===
                    songList.length -
                      1
                      ? 'none'
                      : '1px solid #272727',
                }}
              >
                {/* 순위 */}
                <div
                  style={{
                    width: '34px',
                    height: '34px',
                    flexShrink: 0,
                    display: 'flex',
                    alignItems:
                      'center',
                    justifyContent:
                      'center',
                    borderRadius:
                      '9px',
                    background:
                      index === 0
                        ? '#ff2f7d'
                        : '#252525',
                    color: '#fff',
                    fontWeight: '800',
                    fontSize: '14px',
                  }}
                >
                  {index + 1}
                </div>

                {/* 곡 정보 */}
                <div
                  style={{
                    flex: 1,
                    minWidth: 0,
                  }}
                >
                  <div
                    style={{
                      fontSize:
                        '15px',
                      fontWeight:
                        '600',
                      color:
                        '#f5f5f5',
                      overflow:
                        'hidden',
                      textOverflow:
                        'ellipsis',
                      whiteSpace:
                        'nowrap',
                    }}
                  >
                    {
                      song.songTitle
                    }
                  </div>

                  <div
                    style={{
                      marginTop:
                        '5px',
                      fontSize:
                        '12px',
                      color: '#777',
                    }}
                  >
                    {
                      song.voteCount
                    }
                    표
                  </div>
                </div>

                {/* 투표 버튼 */}
                <button
                  onClick={() =>
                    handleVote(
                      song.songTitle
                    )
                  }
                  style={{
                    flexShrink: 0,
                    border:
                      '1px solid #444',
                    background:
                      '#242424',
                    color: '#eee',
                    padding:
                      '8px 14px',
                    borderRadius:
                      '8px',
                    cursor:
                      'pointer',
                    fontWeight:
                      '600',
                    fontSize:
                      '13px',
                  }}
                >
                  투표
                </button>
              </div>
            )
          )
        )}
      </div>

      {/* 방송자에게만 표시 */}
      {isBroadcaster && (
        <div
          style={{
            marginTop: '18px',
            paddingTop: '18px',
            borderTop:
              '1px solid #303030',
            display: 'flex',
            alignItems: 'center',
            justifyContent:
              'space-between',
            gap: '16px',
          }}
        >
          <div>
            <div
              style={{
                fontSize: '14px',
                fontWeight: '600',
                color: '#ddd',
              }}
            >
              방송자 제어
            </div>

            <div
              style={{
                fontSize: '12px',
                color: '#777',
                marginTop: '4px',
              }}
            >
              현재 투표 1위 곡을 다음
              곡으로 확정합니다.
            </div>
          </div>

          <button
            onClick={
              handleSelectNextSong
            }
            disabled={
              songList.length === 0
            }
            style={{
              flexShrink: 0,
              border: 'none',
              background:
                songList.length ===
                0
                  ? '#333'
                  : '#ff2f7d',
              color:
                songList.length ===
                0
                  ? '#777'
                  : '#fff',
              padding:
                '10px 16px',
              borderRadius: '8px',
              cursor:
                songList.length ===
                0
                  ? 'not-allowed'
                  : 'pointer',
              fontSize: '13px',
              fontWeight: '700',
            }}
          >
            1위 곡 선택
          </button>
        </div>
      )}
    </div>
  );
};

export default LiveVotingRoom;