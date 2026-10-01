import React from 'react';
import { Navigate } from 'react-router-dom';

// 🔒 비로그인 유저의 접근을 막는 라우트 가드 (데스크톱·모바일 공용)
export default function ProtectedRoute({ children }) {
  const user = localStorage.getItem('user');
  const token = localStorage.getItem('token') || localStorage.getItem('accessToken');

  if (!user && !token) {
    alert('로그인이 필요한 서비스입니다.');
    return <Navigate to="/login" replace />;
  }

  return children;
}
