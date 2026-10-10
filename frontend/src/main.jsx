import React from 'react';
import {createRoot} from 'react-dom/client';
import {BrowserRouter} from 'react-router-dom';
import {AuthProvider} from './auth';
import {ToastProvider} from './ui';
import App from './App';
import './style.css';
createRoot(document.getElementById('root')).render(<React.StrictMode><BrowserRouter><ToastProvider><AuthProvider><App/></AuthProvider></ToastProvider></BrowserRouter></React.StrictMode>);
