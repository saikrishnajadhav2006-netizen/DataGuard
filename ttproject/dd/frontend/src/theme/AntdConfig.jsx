import React from 'react';
import { ConfigProvider, theme } from 'antd';

const AntdConfig = ({ children }) => {
  return (
    <ConfigProvider
      theme={{
        algorithm: theme.defaultAlgorithm,
        token: {
          colorPrimary: '#2563EB',
          colorSuccess: '#10B981',
          colorBgContainer: '#FFFFFF',
          colorBgElevated: '#FFFFFF',
          colorText: '#1E293B',
          colorTextSecondary: '#64748B',
          colorBorder: 'rgba(0, 0, 0, 0.06)',
          borderRadius: 12,
          fontFamily: 'Inter, system-ui, sans-serif',
          boxShadow: '0 4px 20px rgba(0, 0, 0, 0.05)',
        },
        components: {
          Menu: {
            itemBg: 'transparent',
            itemSelectedBg: 'rgba(37, 99, 235, 0.08)',
            itemSelectedColor: '#2563EB',
            itemHoverBg: 'rgba(0, 0, 0, 0.02)',
            itemBorderRadius: 8,
          },
          Button: {
            colorPrimary: '#2563EB',
            colorPrimaryHover: '#1D4ED8',
            colorPrimaryActive: '#1E3A8A',
            borderRadius: 8,
          }
        },
      }}
    >
      {children}
    </ConfigProvider>
  );
};

export default AntdConfig;
