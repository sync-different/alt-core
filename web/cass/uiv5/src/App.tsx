/**
 * Main App component for Alterante web application
 * Phase 2: Authentication & Layout
 */

import { useEffect, useState } from 'react';
import { Provider } from 'react-redux';
import { RouterProvider } from 'react-router-dom';
import { store } from './store/store';
import { setAuth } from './store/slices/authSlice';
import { router } from './router';
import { buildUrl } from './utils/urlHelper';
import './App.css';

/**
 * Attempt to restore session from localStorage UUID.
 * Calls nodeinfo.fn to validate the session is still active on the server.
 */
async function tryRestoreSession(): Promise<boolean> {
  const uuid = localStorage.getItem('uuid');
  const username = localStorage.getItem('username');
  if (!uuid) return false;

  try {
    const response = await fetch(buildUrl(`/cass/nodeinfo.fn?uuid=${uuid}`), {
      method: 'GET',
      credentials: 'include',
    });

    if (response.ok) {
      const text = await response.text();
      // nodeinfo.fn returns content only if authenticated — empty means invalid session
      if (text.length > 0) {
        store.dispatch(setAuth({ uuid, username: username || 'user' }));
        return true;
      }
    }
  } catch {
    // Server unreachable — don't restore, let user log in
  }

  // Session invalid — clean up
  localStorage.removeItem('uuid');
  localStorage.removeItem('username');
  return false;
}

function App() {
  const [ready, setReady] = useState(false);

  useEffect(() => {
    tryRestoreSession().finally(() => setReady(true));
  }, []);

  if (!ready) {
    return null; // Brief blank while validating session
  }

  return (
    <Provider store={store}>
      <RouterProvider router={router} />
    </Provider>
  );
}

export default App;
