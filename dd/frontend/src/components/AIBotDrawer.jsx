import React, { useState } from 'react';
import { Drawer, Button, Input } from 'antd';
import { Bot, Send, X } from 'lucide-react';

const AIBotDrawer = () => {
  const [open, setOpen] = useState(false);
  const [messages, setMessages] = useState([
    { role: 'bot', content: 'Hi! I am your Daily Diary AI Assistant. Do you need help creating a new workspace?' }
  ]);
  const [input, setInput] = useState('');

  const handleSend = () => {
    if (!input.trim()) return;
    
    // Add user message
    const newMessages = [...messages, { role: 'user', content: input }];
    setMessages(newMessages);
    setInput('');
    
    // Mock bot response for now
    setTimeout(() => {
      setMessages([...newMessages, { role: 'bot', content: 'This is a mock response. Integration with Gemini is pending.' }]);
    }, 1000);
  };

  return (
    <>
      <Button
        type="primary"
        shape="circle"
        icon={<Bot size={24} />}
        size="large"
        className="fixed bottom-6 right-6 w-14 h-14 flex items-center justify-center z-50 hover:scale-110 transition-transform shadow-lg hover:shadow-xl !bg-dd-accent-blue !border-none"
        onClick={() => setOpen(true)}
      />

      <Drawer
        title={
          <div className="flex items-center gap-2 text-dd-text-main">
            <div className="p-1.5 bg-blue-50 rounded-lg text-dd-accent-blue">
              <Bot size={20} />
            </div>
            <span className="font-bold">DD AI Assistant</span>
          </div>
        }
        placement="right"
        onClose={() => setOpen(false)}
        open={open}
        width={350}
        closeIcon={<X className="text-gray-400 hover:text-gray-600" />}
        styles={{
          header: { background: '#FFFFFF', borderBottom: '1px solid #F1F5F9' },
          body: { background: '#C7F464', padding: '16px', display: 'flex', flexDirection: 'column' },
        }}
      >
        <div className="flex-1 overflow-y-auto flex flex-col gap-4 mb-4">
          {messages.map((msg, index) => (
            <div 
              key={index} 
              className={`max-w-[85%] rounded-[20px] p-4 text-[15px] shadow-sm ${
                msg.role === 'user' 
                  ? 'bg-dd-accent-blue text-white self-end rounded-br-sm' 
                  : 'bg-white border border-gray-100 text-dd-text-main self-start rounded-bl-sm'
              }`}
            >
              {msg.content}
            </div>
          ))}
        </div>
        
        <div className="flex items-center gap-2 mt-auto relative">
          <Input 
            value={input}
            onChange={(e) => setInput(e.target.value)}
            onPressEnter={handleSend}
            placeholder="Ask anything..."
            className="bg-white border-gray-200 text-dd-text-main hover:border-dd-accent-blue focus:border-dd-accent-blue h-12 rounded-xl px-4 shadow-sm"
          />
          <Button 
            type="primary" 
            icon={<Send size={18} />} 
            onClick={handleSend}
            className="flex items-center justify-center h-12 w-12 !rounded-xl shadow-md"
          />
        </div>
      </Drawer>
    </>
  );
};

export default AIBotDrawer;
