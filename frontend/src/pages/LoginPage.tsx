import { useState } from 'react';
import { Link, useNavigate, useLocation } from 'react-router-dom';
import { useAuth } from '../hooks/useAuth';

export default function LoginPage() {
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState('');
  const [success, setSuccess] = useState('');
  const { login, isLoading } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();
  const resetSuccess =
    (location.state as { passwordResetSuccess?: boolean } | null)?.passwordResetSuccess === true;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setSuccess('');

    try {
      await login({ email, password });
      setSuccess('Login successful!');
      navigate('/rooms');
    } catch (err) {
      setError(err instanceof Error ? err.message : 'Login failed');
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-100 px-4 dark:bg-discord-base">
      <div className="bg-white rounded-lg shadow p-8 w-full max-w-md dark:bg-discord-sidebar dark:text-discord-text">
        <h1 className="text-2xl font-bold mb-6 text-center">Login</h1>
        {resetSuccess && (
          <div className="text-green-700 bg-green-50 border border-green-200 rounded px-3 py-2 text-sm mb-4">
            Password changed — sign in with your new password.
          </div>
        )}
        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label htmlFor="email" className="block text-sm font-medium mb-1">
              Email
            </label>
            <input
              id="email"
              type="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              required
              className="w-full border rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-discord-input dark:border-discord-border dark:text-discord-text"
            />
          </div>

          <div>
            <label htmlFor="password" className="block text-sm font-medium mb-1">
              Password
            </label>
            <input
              id="password"
              type="password"
              value={password}
              onChange={(e) => setPassword(e.target.value)}
              required
              className="w-full border rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500 dark:bg-discord-input dark:border-discord-border dark:text-discord-text"
            />
          </div>

          {error && <div className="text-red-500 text-sm">{error}</div>}
          {success && <div className="text-green-600 text-sm">{success}</div>}

          <button
            type="submit"
            disabled={isLoading}
            className="w-full px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600 disabled:bg-gray-400 dark:bg-discord-accent dark:hover:bg-indigo-500"
          >
            {isLoading ? 'Logging in...' : 'Login'}
          </button>
        </form>

        <p className="text-sm text-center mt-4 text-gray-600 dark:text-discord-muted">
          <Link to="/forgot-password" className="text-blue-500 hover:underline dark:text-blue-400">
            Forgot password?
          </Link>
        </p>
        <p className="text-sm text-center mt-6 text-gray-600 dark:text-discord-muted">
          No account?{' '}
          <Link to="/register" className="text-blue-500 hover:underline dark:text-blue-400">
            Sign up
          </Link>
        </p>
      </div>
    </div>
  );
}
