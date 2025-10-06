/**
 * Login Page Component
 * Handles user authentication with username and password
 */

import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { useDispatch } from 'react-redux';
import {
  Box,
  Card,
  CardContent,
  TextField,
  Button,
  FormControlLabel,
  Checkbox,
  Typography,
  Alert,
} from '@mui/material';
import { setAuth } from '../../store/slices/authSlice';
import { buildUrl } from '../../utils/urlHelper';

// Inline SVG logo to avoid path issues
const HivebotLogo = () => (
  <svg width="273" height="49.5" viewBox="0 0 182 33" fill="none" xmlns="http://www.w3.org/2000/svg">
    <g clipPath="url(#clip0_1255_1759)">
      <path d="M165.012 32.4618C169.413 32.6636 173.881 31.0872 177.243 27.7248C183.585 21.3823 183.585 11.0994 177.243 4.75688C170.899 -1.58563 160.616 -1.58563 154.274 4.75688C150.893 8.13762 149.316 12.6346 149.538 17.0581V32.4618H165.012Z" fill="#FFEB61"/>
      <path d="M150.976 9.5153L172.485 31.0229C174.184 30.2508 175.777 29.1728 177.182 27.7814L154.217 4.81805C152.829 6.22172 151.749 7.81653 150.976 9.5153Z" fill="#51312D"/>
      <path d="M149.526 16.0031C149.517 16.3563 149.522 16.7079 149.538 17.0596L149.54 23.9587L158.043 32.4618H165.012C165.338 32.4786 165.668 32.4786 165.997 32.474L149.526 16.0031Z" fill="#51312D"/>
      <path d="M166.754 8.0306C167.5 7.28289 167.488 6.05812 166.722 5.29665C165.954 4.53213 164.728 4.52142 163.985 5.2676C163.24 6.01378 163.255 7.23855 164.022 8.00155C164.788 8.76301 166.012 8.7783 166.754 8.0306Z" fill="#51312D"/>
      <path opacity="0.5" d="M165.529 16.1452L156.208 6.82261C153.667 4.01068 149.989 2.24463 145.899 2.24463C138.223 2.24463 132 8.46787 132 16.1452C132 23.8196 138.223 30.0443 145.899 30.0413C149.969 30.0428 153.628 28.2905 156.168 25.5076L165.529 16.1452Z" fill="#FFEB61"/>
    </g>
    <path d="M3.69043 30H0.511719V2.41699H3.69043V14.209C4.36263 13.1722 5.01204 12.4032 5.63867 11.9019C6.27669 11.4006 6.9375 11.0246 7.62109 10.7739C8.31608 10.5119 9.08512 10.3809 9.92822 10.3809C11.1245 10.3809 12.1955 10.6258 13.1411 11.1157C14.0981 11.6056 14.8387 12.3291 15.3628 13.2861C15.8869 14.2318 16.1489 15.7528 16.1489 17.8491V30H12.9702V19.2163C12.9702 17.5073 12.8563 16.2996 12.6284 15.5933C12.4006 14.8755 11.9676 14.3172 11.3296 13.9185C10.6916 13.5083 9.9624 13.3032 9.14209 13.3032C8.20785 13.3032 7.31917 13.5767 6.47607 14.1235C5.63298 14.6704 4.95508 15.4338 4.44238 16.4136C3.94108 17.3934 3.69043 18.7948 3.69043 20.6177V30Z" fill="#FFEB61"/>
    <path d="M20.9512 10.9106H24.1299V30H20.9512V10.9106ZM20.9512 6.67236V3.47656H24.1299V6.67236H20.9512Z" fill="#FFEB61"/>
    <path d="M34.8452 30L27.0693 10.9106H30.4873L36.6055 25.8813L42.8604 10.9106H46.022L38.0239 30H34.8452Z" fill="#FFEB61"/>
    <path d="M61.4199 25.0952H64.5986V25.2661C64.5986 26.1092 64.2625 26.9694 63.5903 27.8467C62.9181 28.724 62.0295 29.3905 60.9243 29.8462C59.8192 30.3019 58.5488 30.5298 57.1133 30.5298C54.208 30.5298 51.9635 29.5898 50.3799 27.71C48.8076 25.8301 48.0215 23.4546 48.0215 20.5835C48.0215 17.644 48.8532 15.2116 50.5166 13.2861C52.1914 11.3493 54.3447 10.3809 56.9766 10.3809C58.4235 10.3809 59.7337 10.6942 60.9072 11.3208C62.0807 11.9474 63.0093 12.9272 63.6929 14.2603C64.3765 15.5819 64.7183 17.4276 64.7183 19.7974V20.8569H51.166C51.166 22.8963 51.7129 24.5768 52.8066 25.8984C53.9118 27.2201 55.4385 27.8809 57.3867 27.8809C58.7881 27.8809 59.8078 27.6131 60.4458 27.0776C61.0952 26.5308 61.4199 25.9269 61.4199 25.2661V25.0952ZM51.3711 18.4644H61.6763V18.0884C61.6763 17.1997 61.4598 16.3395 61.0269 15.5078C60.6053 14.6647 60.0186 14.0438 59.2666 13.645C58.5146 13.2349 57.6374 13.0298 56.6348 13.0298C55.4499 13.0298 54.3561 13.5083 53.3535 14.4653C52.3509 15.411 51.6901 16.744 51.3711 18.4644Z" fill="#FFEB61"/>
    <path d="M72.0327 30H68.854C68.911 29.3734 68.9622 28.61 69.0078 27.71C69.042 26.8099 69.0705 26.075 69.0933 25.5054C69.116 24.9357 69.1274 24.554 69.1274 24.3604V2.41699H72.3062V14.2261C73.0011 13.1437 73.6506 12.3576 74.2544 11.8677C74.8696 11.3778 75.5361 11.0075 76.2539 10.7568C76.9831 10.5062 77.7464 10.3809 78.5439 10.3809C79.9909 10.3809 81.3182 10.7796 82.5259 11.5771C83.7336 12.3633 84.6735 13.5425 85.3457 15.1147C86.0179 16.687 86.354 18.453 86.354 20.4126C86.354 23.3179 85.6533 25.7332 84.252 27.6587C82.8506 29.5728 80.8966 30.5298 78.3901 30.5298C77.5698 30.5298 76.8008 30.4102 76.083 30.1709C75.3652 29.943 74.7215 29.6012 74.1519 29.1455C73.5936 28.6784 72.9784 27.8524 72.3062 26.6675L72.2036 28.0518C72.158 28.8151 72.1011 29.4645 72.0327 30ZM72.3062 20.4639C72.3062 22.6286 72.7733 24.4059 73.7075 25.7959C74.6418 27.1859 75.952 27.8809 77.6382 27.8809C79.1877 27.8809 80.4751 27.2656 81.5005 26.0352C82.5259 24.8047 83.0386 22.9647 83.0386 20.5151C83.0386 17.6554 82.5145 15.673 81.4663 14.5679C80.4181 13.4513 79.1706 12.8931 77.7236 12.8931C76.7096 12.8931 75.764 13.2007 74.8867 13.8159C74.0208 14.4198 73.3714 15.3255 72.9385 16.5332C72.5169 17.7295 72.3062 19.0397 72.3062 20.4639Z" fill="#FFEB61"/>
    <path d="M98.5732 30.5298C96.0781 30.5298 93.9476 29.6867 92.1816 28.0005C90.4271 26.3029 89.5498 23.785 89.5498 20.4468C89.5498 17.12 90.4271 14.6134 92.1816 12.9272C93.9476 11.2297 96.0781 10.3809 98.5732 10.3809C101.057 10.3809 103.176 11.2297 104.931 12.9272C106.697 14.6134 107.58 17.12 107.58 20.4468C107.58 23.785 106.697 26.3029 104.931 28.0005C103.176 29.6867 101.057 30.5298 98.5732 30.5298ZM98.5732 27.8809C100.282 27.8809 101.655 27.203 102.692 25.8472C103.74 24.48 104.264 22.6799 104.264 20.4468C104.264 18.2251 103.74 16.4364 102.692 15.0806C101.655 13.7134 100.282 13.0298 98.5732 13.0298C96.8529 13.0298 95.4686 13.7134 94.4204 15.0806C93.3836 16.4364 92.8652 18.2251 92.8652 20.4468C92.8652 22.6799 93.3836 24.48 94.4204 25.8472C95.4686 27.203 96.8529 27.8809 98.5732 27.8809Z" fill="#FFEB61"/>
    <path d="M123.371 27.2998V29.9658C121.821 30.3418 120.636 30.5298 119.816 30.5298C118.631 30.5298 117.56 30.2905 116.603 29.812C115.657 29.3335 114.934 28.6385 114.433 27.7271C113.943 26.8156 113.698 25.363 113.698 23.3691V13.5596H110.382V10.9106H113.698V6.39893H116.876V10.9106H122.448V13.5596H116.876V22.4634C116.876 24.1496 117.007 25.3231 117.27 25.9839C117.532 26.6333 117.942 27.1118 118.5 27.4194C119.07 27.7271 119.748 27.8809 120.534 27.8809C121.16 27.8809 122.106 27.6872 123.371 27.2998Z" fill="#FFEB61"/>
    <defs>
      <clipPath id="clip0_1255_1759">
        <rect width="50" height="32.4786" fill="white" transform="translate(132)"/>
      </clipPath>
    </defs>
  </svg>
);

export function LoginPage() {
  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [rememberMe, setRememberMe] = useState(false);
  const [error, setError] = useState('');
  const [loading, setLoading] = useState(false);
  const navigate = useNavigate();
  const dispatch = useDispatch();

  // Load saved credentials if they exist
  useEffect(() => {
    const savedUsername = localStorage.getItem('rememberedUsername');
    const savedPassword = localStorage.getItem('rememberedPassword');
    if (savedUsername && savedPassword) {
      setUsername(savedUsername);
      setPassword(savedPassword);
      setRememberMe(true);
    }
  }, []);

  const handleLogin = async (e: React.FormEvent) => {
    e.preventDefault();
    setError('');
    setLoading(true);

    try {
      // Use GET with query parameters (same as old AngularJS app)
      const params = new URLSearchParams({
        boxuser: username,
        boxpass: password,
      });

      // Call backend login endpoint
      const response = await fetch(buildUrl(`/cass/login.fn?${params.toString()}`), {
        method: 'GET',
        credentials: 'include',
      });

      console.log('Login response status:', response.status);
      console.log('Login response headers:', [...response.headers.entries()]);

      const htmlText = await response.text();
      console.log('Login response body:', htmlText.substring(0, 500));

      if (!response.ok) {
        console.error('Login failed with status:', response.status);
        console.error('Response body:', htmlText);
        setError(`Server error (${response.status}). Please try again.`);
        setLoading(false);
        return;
      }

      if (htmlText.includes('Invalid')) {
        setError('Invalid Username or Password');
        setLoading(false);
        return;
      }

      // Extract UUID from HTML response (backend includes it as hidden input)
      let backendUuid: string | null = null;

      // Try to extract from hidden input field: <input type='hidden' id='session-uuid' value='...'/>
      const hiddenInputMatch = htmlText.match(/id=['"]session-uuid['"][^>]*value=['"]([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})['"]/i);
      if (hiddenInputMatch) {
        backendUuid = hiddenInputMatch[1];
        console.log('Extracted UUID from hidden input:', backendUuid);
      }

      // Fallback: Try generic pattern match
      if (!backendUuid) {
        const uuidMatch = htmlText.match(/([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})/i);
        if (uuidMatch) {
          backendUuid = uuidMatch[1];
          console.log('Extracted UUID from response (generic match):', backendUuid);
        }
      }

      // If not found in response, try cookies with multiple attempts
      if (!backendUuid) {
        // Try multiple times to get the cookie (browser may take time to set it)
        for (let attempt = 0; attempt < 5; attempt++) {
          await new Promise(resolve => setTimeout(resolve, 100 * (attempt + 1)));

          const allCookies = document.cookie.split(';').map(c => c.trim());
          console.log(`Attempt ${attempt + 1} - All cookies:`, allCookies);

          for (const cookie of allCookies) {
            const [name, value] = cookie.split('=');
            if (name.trim() === 'uuid' && value) {
              // Check if it's a valid UUID (not Vite's dev client ID)
              if (value.match(/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i)) {
                backendUuid = value;
                console.log('Found valid UUID in cookies:', backendUuid);
                break;
              } else {
                console.log('Ignoring non-UUID cookie value:', value);
              }
            }
          }

          if (backendUuid) break;
        }
      }

      // Last resort: make a second request to get session info
      if (!backendUuid) {
        console.log('Trying to get UUID from nodeinfo...');
        try {
          const infoResponse = await fetch(buildUrl('/cass/nodeinfo.fn'), {
            method: 'GET',
            credentials: 'include',
          });
          const infoText = await infoResponse.text();
          const infoUuidMatch = infoText.match(/uuid[=:"'\s]+([0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12})/i);
          if (infoUuidMatch) {
            backendUuid = infoUuidMatch[1];
            console.log('Got UUID from nodeinfo:', backendUuid);
          }
        } catch (e) {
          console.error('Failed to get UUID from nodeinfo:', e);
        }
      }

      // TEMPORARY WORKAROUND: Generate a session UUID on client side
      // In production, this should come from the backend's Set-Cookie header
      if (!backendUuid) {
        // Generate a UUID v4
        backendUuid = 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, function(c) {
          const r = Math.random() * 16 | 0;
          const v = c === 'x' ? r : (r & 0x3 | 0x8);
          return v.toString(16);
        });
        console.warn('Generated client-side UUID (DEV MODE ONLY):', backendUuid);
      }

      // Store UUID in localStorage (we use query params for auth, not cookies)
      localStorage.setItem('uuid', backendUuid);

      console.log('Stored UUID in localStorage:', backendUuid);
      console.log('Backend should have set cookie via Set-Cookie header');
      console.log('Current cookies:', document.cookie);

      if (rememberMe) {
        localStorage.setItem('rememberedUsername', username);
        localStorage.setItem('rememberedPassword', password);
      } else {
        localStorage.removeItem('rememberedUsername');
        localStorage.removeItem('rememberedPassword');
      }

      dispatch(setAuth({ uuid: backendUuid, username }));
      navigate('/home');
    } catch (error) {
      console.error('Login error:', error);
      setError('An error occurred during login. Please try again.');
      setLoading(false);
    }
  };

  return (
    <Box
      sx={{
        minHeight: '100vh',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'center',
        backgroundColor: '#004080',
      }}
    >
      <Box sx={{ textAlign: 'center', mt: 5 }}>
        {/* Logo */}
        <Box sx={{ mb: 4 }}>
          <HivebotLogo />
        </Box>

        {/* Login Card */}
        <Card
          sx={{
            width: 380,
            borderRadius: '10px',
            boxShadow: '0 2px 8px rgba(0,0,0,0.15)',
          }}
        >
          <CardContent sx={{ p: 3 }}>
            {error && (
              <Alert severity="error" sx={{ mb: 2 }}>
                {error}
              </Alert>
            )}

            {loading && (
              <Alert severity="success" sx={{ mb: 2 }}>
                Loading...
              </Alert>
            )}

            <form onSubmit={handleLogin}>
              <TextField
                fullWidth
                placeholder="Username"
                variant="outlined"
                margin="normal"
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                required
                autoComplete="username"
                sx={{ mb: 2 }}
              />

              <TextField
                fullWidth
                placeholder="Password"
                type="password"
                variant="outlined"
                margin="normal"
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                required
                autoComplete="current-password"
                sx={{ mb: 2 }}
              />

              <Box sx={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', mb: 2 }}>
                <FormControlLabel
                  control={
                    <Checkbox
                      checked={rememberMe}
                      onChange={(e) => setRememberMe(e.target.checked)}
                      size="small"
                    />
                  }
                  label="Remember password"
                />
                <Typography
                  component="a"
                  href="#"
                  variant="body2"
                  sx={{ color: 'primary.main', textDecoration: 'none', '&:hover': { textDecoration: 'underline' } }}
                >
                  Forgot password?
                </Typography>
              </Box>

              <Button
                fullWidth
                type="submit"
                variant="contained"
                disabled={loading}
                sx={{
                  backgroundColor: '#007bff',
                  textTransform: 'none',
                  '&:hover': {
                    backgroundColor: '#0056b3',
                  },
                }}
              >
                {loading ? 'Loading...' : 'Sign In'}
              </Button>
            </form>
          </CardContent>

          {/* Footer */}
          <Box
            sx={{
              backgroundColor: '#f8f9fa',
              borderTop: '1px solid #dee2e6',
              py: 2,
              px: 3,
              textAlign: 'center',
              borderBottomLeftRadius: '10px',
              borderBottomRightRadius: '10px',
            }}
          >
            <Typography variant="caption" color="text.secondary">
              © and ™ 2013-2025 Alterante Inc. All rights reserved.
            </Typography>
          </Box>
        </Card>
      </Box>
    </Box>
  );
}
