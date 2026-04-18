import { useState, useCallback, useEffect } from 'react';
import { User, RegisterPayload, LoginPayload, AuthContextType } from '../types/auth';
import { authService } from '../services/authService';

export const useAuth = (): AuthContextType => {
  const [user, setUser] = useState<User | null>(null);
  const [token, setToken] = useState<string | null>(null);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    const storedToken = authService.getStoredToken();
    if (storedToken) {
      authService
        .getCurrentUser(storedToken)
        .then((userData) => {
          setUser(userData);
          setToken(storedToken);
        })
        .catch(() => {
          authService.removeAuthToken();
          setUser(null);
          setToken(null);
        })
        .finally(() => {
          setIsLoading(false);
        });
    } else {
      setIsLoading(false);
    }
  }, []);

  const register = useCallback(async (payload: RegisterPayload) => {
    setIsLoading(true);
    try {
      const userData = await authService.register(payload);
      setUser(userData);
    } catch (error) {
      throw error;
    } finally {
      setIsLoading(false);
    }
  }, []);

  const login = useCallback(async (payload: LoginPayload) => {
    setIsLoading(true);
    try {
      const response = await authService.login(payload);
      setUser(response.user);
      setToken(response.token);
      authService.setAuthToken(response.token);
    } catch (error) {
      throw error;
    } finally {
      setIsLoading(false);
    }
  }, []);

  const logout = useCallback(() => {
    authService.removeAuthToken();
    setUser(null);
    setToken(null);
  }, []);

  return {
    user,
    token,
    isLoading,
    register,
    login,
    logout,
    isAuthenticated: user !== null && token !== null,
  };
};
