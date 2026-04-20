import { useState } from 'react';
import { Link } from 'react-router-dom';
import { passwordResetService } from '../services/passwordResetService';

export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('');
  const [submitted, setSubmitted] = useState(false);
  const [busy, setBusy] = useState(false);

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    setBusy(true);
    try {
      await passwordResetService.request(email);
    } catch {
      // Swallow — enumeration-safe response is always 204, network error is rare.
    } finally {
      setSubmitted(true);
      setBusy(false);
    }
  };

  return (
    <div className="min-h-screen flex items-center justify-center bg-gray-100 px-4">
      <div className="bg-white rounded-lg shadow p-8 w-full max-w-md">
        <h1 className="text-2xl font-bold mb-6 text-center">Forgot password</h1>
        {submitted ? (
          <div className="text-sm text-gray-700">
            If an account exists for that email, we sent a reset link. Check your inbox.
          </div>
        ) : (
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
                className="w-full border rounded px-3 py-2 focus:outline-none focus:ring-2 focus:ring-blue-500"
              />
            </div>
            <button
              type="submit"
              disabled={busy}
              className="w-full px-4 py-2 bg-blue-500 text-white rounded hover:bg-blue-600 disabled:bg-gray-400"
            >
              {busy ? 'Sending…' : 'Send reset link'}
            </button>
          </form>
        )}
        <p className="text-sm text-center mt-6 text-gray-600">
          <Link to="/login" className="text-blue-500 hover:underline">
            Back to sign in
          </Link>
        </p>
      </div>
    </div>
  );
}
