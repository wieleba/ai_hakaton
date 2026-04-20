import { BrowserRouter as Router, Routes, Route, Navigate } from 'react-router-dom';
import RegisterPage from './pages/RegisterPage';
import LoginPage from './pages/LoginPage';
import ForgotPasswordPage from './pages/ForgotPasswordPage';
import ResetPasswordPage from './pages/ResetPasswordPage';
import AuthGuard from './components/AuthGuard';
import { AppShell } from './layout/AppShell';
import { RoomListPage } from './pages/RoomListPage';
import { ChatPage } from './pages/ChatPage';
import { FriendsPage } from './pages/FriendsPage';
import { DirectMessagesPage } from './pages/DirectMessagesPage';
import { DirectChatPage } from './pages/DirectChatPage';
import { SessionsPage } from './pages/SessionsPage';
import { AccountSettingsPage } from './pages/AccountSettingsPage';

export default function App() {
  return (
    <Router>
      <div className="min-h-screen bg-gray-100">
        <Routes>
          <Route path="/register" element={<RegisterPage />} />
          <Route path="/login" element={<LoginPage />} />
          <Route path="/forgot-password" element={<ForgotPasswordPage />} />
          <Route path="/reset-password" element={<ResetPasswordPage />} />
          <Route path="/dashboard" element={<Navigate to="/rooms" replace />} />
          <Route
            element={
              <AuthGuard>
                <AppShell />
              </AuthGuard>
            }
          >
            <Route path="/rooms" element={<RoomListPage />} />
            <Route path="/rooms/:roomId" element={<ChatPage />} />
            <Route path="/friends" element={<FriendsPage />} />
            <Route path="/dms" element={<DirectMessagesPage />} />
            <Route path="/dms/:conversationId" element={<DirectChatPage />} />
            <Route path="/sessions" element={<SessionsPage />} />
            <Route path="/account" element={<AccountSettingsPage />} />
            <Route path="/" element={<Navigate to="/rooms" replace />} />
          </Route>
        </Routes>
      </div>
    </Router>
  );
}
