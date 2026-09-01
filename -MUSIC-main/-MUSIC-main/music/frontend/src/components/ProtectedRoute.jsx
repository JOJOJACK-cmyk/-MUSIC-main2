import React from 'react';
import { Navigate } from 'react-router-dom';

export default function ProtectedRoute({ children }) {
  // 로컬 스토리지나 상태 관리에서 로그인 토큰/유저 정보 확인
  // (프로젝트에서 쓰는 토큰 이름으로 맞춰주세요: 'token', 'accessToken' 등)
  const token = localStorage.getItem('token');

  // 토큰이 없으면 로그인 페이지로 리다이렉트
  if (!token) {
    alert('로그인이 필요한 서비스입니다.');
    return <Navigate to="/login" replace />;
  }

  // 로그인이 되어 있으면 요청한 컴포넌트(자식 요소)를 그대로 보여줌
  return children;
}