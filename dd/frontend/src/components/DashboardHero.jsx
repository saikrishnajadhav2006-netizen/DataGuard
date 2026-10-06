import React from 'react';

const DashboardHero = () => {
  return (
    <div className="flex flex-col gap-8 pb-10">
      {/* Hero Video Section */}
      <div className="relative w-full h-[300px] md:h-[400px] rounded-[24px] overflow-hidden bg-white shadow-soft border border-gray-100 p-2 group hover:shadow-soft-hover transition-all duration-300">
        <div className="relative w-full h-full rounded-[16px] overflow-hidden">
          <video 
            className="absolute inset-0 w-full h-full object-cover"
            src="/animation.mp4" 
            muted 
            loop 
            autoPlay 
            playsInline
          />
          <div className="absolute inset-0 bg-gradient-to-t from-black/60 via-black/20 to-transparent opacity-90" />
          <div className="absolute bottom-6 left-6 md:bottom-10 md:left-10 max-w-2xl">
            <h1 className="text-4xl md:text-5xl font-bold text-white mb-4 tracking-tight drop-shadow-md">
              Organize. <span className="text-blue-300">Build.</span> Grow.
            </h1>
            <p className="text-gray-200 text-lg md:text-xl drop-shadow-sm">
              Welcome to your Dynamic Workspace. Create custom boards for any process, drop in widgets, and track your growth seamlessly.
            </p>
          </div>
        </div>
      </div>

      {/* Activity Heatmap Placeholder */}
      <div className="bg-white rounded-[20px] p-8 shadow-soft border border-gray-100 relative overflow-hidden group hover:shadow-soft-hover transition-all duration-300">
        <div className="absolute -top-32 -right-32 w-96 h-96 bg-blue-50 rounded-full blur-[80px] pointer-events-none" />
        
        <h2 className="text-2xl font-bold text-dd-text-main mb-6 relative z-10">Activity Streaks</h2>
        <div className="h-48 bg-gray-50 rounded-[16px] border border-gray-100 flex items-center justify-center relative z-10">
          <span className="text-dd-text-muted font-medium">Your contribution heatmap will appear here...</span>
        </div>
      </div>
    </div>
  );
};

export default DashboardHero;
