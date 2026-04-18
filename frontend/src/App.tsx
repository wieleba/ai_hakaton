import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import RegisterPage from './pages/RegisterPage';
import LoginPage from './pages/LoginPage';
import AuthGuard from './components/AuthGuard';
import { AppSidebar } from './components/AppSidebar';
import { RoomListPage } from './pages/RoomListPage';
import { ChatPage } from './pages/ChatPage';
import { FriendsPage } from './pages/FriendsPage';
import { DirectMessagesPage } from './pages/DirectMessagesPage';
import { DirectChatPage } from './pages/DirectChatPage';

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
                <AppSidebar><RoomListPage /></AppSidebar>
              </AuthGuard>
            }
          />
          <Route
            path="/rooms/:roomId"
            element={
              <AuthGuard>
                <AppSidebar><ChatPage /></AppSidebar>
              </AuthGuard>
            }
          />
          <Route
            path="/friends"
            element={
              <AuthGuard>
                <AppSidebar><FriendsPage /></AppSidebar>
              </AuthGuard>
            }
          />
          <Route
            path="/dms"
            element={
              <AuthGuard>
                <AppSidebar><DirectMessagesPage /></AppSidebar>
              </AuthGuard>
            }
          />
          <Route
            path="/dms/:conversationId"
            element={
              <AuthGuard>
                <AppSidebar><DirectChatPage /></AppSidebar>
              </AuthGuard>
            }
          />
          <Route path="/" element={<Navigate to="/dashboard" replace />} />
        </Routes>
      </div>
    </Router>
  );
}
