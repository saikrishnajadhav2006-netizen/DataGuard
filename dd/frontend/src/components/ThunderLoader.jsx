import React from 'react';
import { Zap } from 'lucide-react';

const ThunderLoader = ({ fullScreen = false }) => {
  const content = (
    <div className="flex flex-col items-center justify-center gap-4">
      <div className="relative flex items-center justify-center">
        {/* Pulsating glowing rings */}
        <div className="absolute w-20 h-20 bg-[#06B6D4] rounded-full blur-[25px] animate-pulse opacity-40"></div>
        <div className="absolute w-12 h-12 border-2 border-[#06B6D4] rounded-full animate-ping opacity-60"></div>
        
        {/* Core Thunder Icon */}
        <Zap 
          size={48} 
          className="text-[#06B6D4] drop-shadow-[0_0_15px_rgba(6,182,212,0.8)] z-10 animate-bounce" 
          fill="currentColor"
        />
      </div>
      <div className="text-[#06B6D4] font-semibold tracking-widest uppercase text-sm mt-2 animate-pulse glow-text">
        Loading...
      </div>
    </div>
  );

  if (fullScreen) {
    return (
      <div className="fixed inset-0 z-50 flex items-center justify-center bg-[rgba(9,13,22,0.8)] backdrop-blur-md">
        {content}
      </div>
    );
  }

  return (
    <div className="w-full h-full min-h-[200px] flex items-center justify-center">
      {content}
    </div>
  );
};

export default ThunderLoader;
