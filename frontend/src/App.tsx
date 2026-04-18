import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import RegisterPage from './pages/RegisterPage';
import LoginPage from './pages/LoginPage';
import AuthGuard from './components/AuthGuard';
import { RoomListPage } from './pages/RoomListPage';
import { ChatPage } from './pages/ChatPage';

export default function App() {
  return (
    <Router>
      <div className="min-h-screen bg-gray-100">
        <Routes>
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route
            path="/dashboard"
            element={
              <AuthGuard>
                <div className="flex items-center justify-center min-h-screen">
                  <h1 className="text-4xl font-bold">Dashboard</h1>
                </div>
              </AuthGuard>
            }
          />
          <Route
            path="/rooms"
            element={
              <AuthGuard>
                <RoomListPage />
              </AuthGuard>
            }
          />
          <Route
            path="/rooms/:roomId"
            element={
              <AuthGuard>
                <ChatPage />
              </AuthGuard>
            }
          />
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </div>
    </Router>
  );
}
