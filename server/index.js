import { apiApp } from './api.js';

const PORT = process.env.PORT || 5001;

apiApp.listen(PORT, '0.0.0.0', () => {
  console.log(`🚀 Shardeya Real Estate Operating System API running on http://localhost:${PORT}`);
});
