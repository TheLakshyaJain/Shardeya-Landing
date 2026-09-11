import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
import { apiApp } from './server/api.js';

// https://vitejs.dev/config/
export default defineConfig({
  plugins: [
    react(),
    {
      name: 'shardeya-api-server',
      configureServer(server) {
        server.middlewares.use(apiApp);
      }
    }
  ],
  server: {
    port: 5173,
    host: '0.0.0.0',
    open: false
  }
});
