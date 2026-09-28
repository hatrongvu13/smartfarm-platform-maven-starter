import React from 'react'
import { createRoot } from 'react-dom/client'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { AuthProvider } from './auth'
import App from './app'
import './styles.css'
const client = new QueryClient({ defaultOptions: { queries: { retry: false, refetchOnWindowFocus: false } } })
createRoot(document.getElementById('root')!).render(<React.StrictMode><QueryClientProvider client={client}><AuthProvider><App /></AuthProvider></QueryClientProvider></React.StrictMode>)
