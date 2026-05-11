import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';
export default defineConfig({
    plugins: [react()],
    server: {
        port: Number(process.env.VITE_PORT || 5173),
        proxy: {
            '/v1/api/wealth': {
                target: process.env.VITE_WEALTH_API_TARGET || 'http://localhost:8083',
                changeOrigin: true
            },
            '/v1/api/budget': {
                target: process.env.VITE_BUDGET_API_TARGET || 'http://localhost:8084',
                changeOrigin: true
            },
            '/v1/api/investments': {
                target: process.env.VITE_INVESTMENTS_API_TARGET || 'http://localhost:8082',
                changeOrigin: true
            },
            '/v1/api': {
                target: process.env.VITE_API_TARGET || 'http://localhost:8081',
                changeOrigin: true
            }
        }
    }
});
