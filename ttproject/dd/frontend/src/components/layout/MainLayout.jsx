import React, { useState } from 'react';
import { Layout, Menu, Button, Drawer } from 'antd';
import { Outlet, useNavigate, useLocation } from 'react-router-dom';
import { 
  User,
  Menu as MenuIcon,
  Zap,
  Plus,
  FolderOpen
} from 'lucide-react';

const { Header, Sider, Content } = Layout;

const MainLayout = () => {
  const [collapsed, setCollapsed] = useState(false);
  const [mobileVisible, setMobileVisible] = useState(false);
  const navigate = useNavigate();
  const location = useLocation();

  // Mock workspaces fetched from backend
  const workspaces = [
    { id: 1, title: 'My Fitness Board' },
    { id: 2, title: 'DSA Prep 2026' },
    { id: 3, title: 'Startup Project' },
  ];

  const menuItems = [
    { key: '/', icon: <Zap size={20} />, label: 'Overview Dashboard' },
    { type: 'divider' },
    ...workspaces.map(ws => ({
      key: `/workspace/${ws.id}`,
      icon: <FolderOpen size={18} />,
      label: ws.title
    }))
  ];

  const MenuContent = (
    <>
      <div className="flex items-center gap-3 px-6 py-6 mb-2 border-b border-gray-100">
        <div className="w-10 h-10 rounded-full border border-gray-200 flex items-center justify-center overflow-hidden bg-white shadow-sm">
          <User className="text-dd-accent-blue" size={20} />
        </div>
        {!collapsed && (
          <div className="flex flex-col">
            <span className="font-semibold text-dd-text-main">Sai Krishna</span>
            <span className="text-xs text-dd-text-muted">Pro Member</span>
          </div>
        )}
      </div>
      
      <div className="px-4 py-2 flex justify-between items-center">
        {!collapsed && <span className="text-xs font-bold text-gray-400 uppercase tracking-wider">Your Workspaces</span>}
        <Button type="text" size="small" icon={<Plus size={16} />} className="text-dd-accent-blue" />
      </div>

      <Menu
        mode="inline"
        selectedKeys={[location.pathname]}
        items={menuItems}
        onClick={({ key }) => {
          navigate(key);
          setMobileVisible(false);
        }}
        className="px-2 border-r-0 !bg-transparent"
      />
    </>
  );

  return (
    <Layout className="min-h-screen !bg-dd-bg-primary">
      {/* Mobile Sidebar */}
      <Drawer
        placement="left"
        closable={false}
        onClose={() => setMobileVisible(false)}
        open={mobileVisible}
        width={250}
        styles={{ body: { padding: 0, backgroundColor: '#C7F464' } }}
        className="md:hidden"
      >
        {MenuContent}
      </Drawer>

      {/* Desktop Sidebar */}
      <Sider 
        collapsible 
        collapsed={collapsed} 
        onCollapse={(value) => setCollapsed(value)}
        width={250}
        theme="light"
        className="hidden md:block !bg-[#C7F464] border-r border-gray-200"
      >
        {MenuContent}
      </Sider>

      <Layout className="bg-transparent">
        <Header className="flex items-center justify-between px-6 bg-white/80 backdrop-blur-md border-b border-gray-200 h-16 sticky top-0 z-10 shadow-sm">
          <div className="flex items-center gap-4">
            <Button 
              type="text" 
              icon={<MenuIcon className="text-gray-500" />} 
              onClick={() => setMobileVisible(true)}
              className="md:hidden"
            />
            <div className="flex items-center gap-2">
              <div className="flex items-center justify-center text-dd-accent-blue bg-blue-50 p-1.5 rounded-lg">
                <Zap size={24} fill="currentColor" />
              </div>
              <span className="font-bold text-xl text-dd-text-main tracking-wide">Daily Diary</span>
            </div>
          </div>

          <div className="flex items-center gap-4">
            <Button type="primary" className="font-medium text-white shadow-md hover:shadow-lg transition-shadow">
              Login
            </Button>
          </div>
        </Header>

        <Content className="p-6 md:p-8 overflow-y-auto">
          {/* Transition wrapper */}
          <div className="h-full page-transition-enter-active">
            <Outlet />
          </div>
        </Content>
      </Layout>
    </Layout>
  );
};

export default MainLayout;
