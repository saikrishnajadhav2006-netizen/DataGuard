import React, { useState } from 'react';
import { Check, Flame, Droplets, Activity, Brain } from 'lucide-react';
import { Switch, Progress } from 'antd';

const FitnessLogs = () => {
  const [habits, setHabits] = useState([
    { id: 1, text: 'Morning 10k Steps', completed: false },
    { id: 2, text: 'Read 20 Pages', completed: true },
    { id: 3, text: 'No Sugar Today', completed: false },
    { id: 4, text: 'Code for 2 Hours', completed: true },
  ]);

  const toggleHabit = (id) => {
    setHabits(habits.map(h => h.id === id ? { ...h, completed: !h.completed } : h));
  };

  return (
    <div className="flex flex-col gap-6">
      <div className="flex items-center justify-between mb-2">
        <h1 className="text-3xl font-bold text-[#F8FAFC]">Fitness & Habits</h1>
        <div className="px-4 py-1.5 rounded-full bg-[rgba(16,185,129,0.1)] border border-[rgba(16,185,129,0.3)] text-[#10B981] font-medium flex items-center gap-2 glow-green">
          <Flame size={16} fill="currentColor" />
          <span>Active Streak: 14 Days</span>
        </div>
      </div>

      {/* Grid of 4 Metric Cards */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-4 gap-6">
        
        {/* Card 1: Water Goal */}
        <div className="glass-card p-5 relative overflow-hidden group hover:shadow-[0_0_20px_rgba(6,182,212,0.3)] transition-all duration-300">
          <div className="flex justify-between items-start mb-4">
            <h3 className="text-[#94A3B8] font-medium">Water Goal</h3>
            <div className="p-2 rounded-lg bg-[rgba(6,182,212,0.1)] text-[#06B6D4]">
              <Droplets size={20} />
            </div>
          </div>
          <div className="flex items-end gap-2 mb-2">
            <span className="text-3xl font-bold text-[#F8FAFC]">2.4</span>
            <span className="text-[#94A3B8] mb-1">/ 3.0 L</span>
          </div>
          <Progress percent={80} strokeColor="#06B6D4" trailColor="rgba(255,255,255,0.05)" showInfo={false} size="small" />
        </div>

        {/* Card 2: Workout/Steps Tracker */}
        <div className="glass-card p-5 relative overflow-hidden group hover:shadow-[0_0_20px_rgba(16,185,129,0.3)] transition-all duration-300">
          <div className="flex justify-between items-start mb-4">
            <h3 className="text-[#94A3B8] font-medium">Activity</h3>
            <div className="p-2 rounded-lg bg-[rgba(16,185,129,0.1)] text-[#10B981]">
              <Activity size={20} />
            </div>
          </div>
          <div className="flex items-center gap-4">
            <Progress type="circle" percent={65} size={60} strokeColor="#10B981" trailColor="rgba(255,255,255,0.05)" format={() => '6.5k'} />
            <div className="flex flex-col">
              <span className="text-sm text-[#F8FAFC] font-medium">Steps</span>
              <span className="text-xs text-[#94A3B8]">Goal: 10,000</span>
            </div>
          </div>
        </div>

        {/* Card 3: Active Streak */}
        <div className="glass-card p-5 relative overflow-hidden group hover:shadow-[0_0_20px_rgba(245,158,11,0.3)] transition-all duration-300 flex flex-col justify-center items-center">
          <div className="absolute top-0 right-0 w-24 h-24 bg-[#F59E0B] opacity-[0.03] rounded-bl-full pointer-events-none" />
          <Flame size={40} className="text-[#F59E0B] mb-2 drop-shadow-[0_0_15px_rgba(245,158,11,0.6)] animate-pulse" fill="currentColor" />
          <h3 className="text-2xl font-bold text-[#F8FAFC]">14 Days</h3>
          <span className="text-[#94A3B8] text-sm">Consistent</span>
        </div>

        {/* Card 4: Mind & Focus */}
        <div className="glass-card p-5 relative overflow-hidden group hover:shadow-[0_0_20px_rgba(168,85,247,0.3)] transition-all duration-300">
          <div className="flex justify-between items-start mb-4">
            <h3 className="text-[#94A3B8] font-medium">Focus Sessions</h3>
            <div className="p-2 rounded-lg bg-[rgba(168,85,247,0.1)] text-[#A855F7]">
              <Brain size={20} />
            </div>
          </div>
          <div className="flex items-end gap-2 mb-2">
            <span className="text-3xl font-bold text-[#F8FAFC]">45</span>
            <span className="text-[#94A3B8] mb-1">mins today</span>
          </div>
          <Progress percent={45} strokeColor="#A855F7" trailColor="rgba(255,255,255,0.05)" showInfo={false} size="small" />
        </div>

      </div>

      {/* Daily Checklist */}
      <div className="glass-card p-6 mt-2">
        <h2 className="text-lg font-semibold text-[#F8FAFC] mb-4">Daily Checklist</h2>
        <div className="flex flex-col gap-3">
          {habits.map((habit) => (
            <div key={habit.id} className="flex items-center justify-between p-3 rounded-xl bg-[rgba(255,255,255,0.02)] hover:bg-[rgba(255,255,255,0.04)] border border-[rgba(255,255,255,0.05)] transition-colors">
              <div className="flex items-center gap-3">
                <div className={`w-8 h-8 rounded-full flex items-center justify-center transition-colors ${habit.completed ? 'bg-[rgba(16,185,129,0.2)] text-[#10B981]' : 'bg-[rgba(255,255,255,0.05)] text-transparent'}`}>
                  {habit.completed && <Check size={16} strokeWidth={3} />}
                </div>
                <span className={`text-base font-medium transition-all ${habit.completed ? 'text-[#94A3B8] line-through decoration-[#94A3B8]/50' : 'text-[#F8FAFC]'}`}>
                  {habit.text}
                </span>
              </div>
              <Switch 
                checked={habit.completed} 
                onChange={() => toggleHabit(habit.id)}
                className={habit.completed ? 'bg-[#10B981]' : 'bg-gray-600'}
              />
            </div>
          ))}
        </div>
      </div>

    </div>
  );
};

export default FitnessLogs;
