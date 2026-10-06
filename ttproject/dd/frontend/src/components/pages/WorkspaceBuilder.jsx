import React, { useState } from 'react';
import { useParams } from 'react-router-dom';
import { Plus, Image as ImageIcon, Video, CheckSquare, Type, GripVertical, Trash2 } from 'lucide-react';
import { Button, Input, Dropdown } from 'antd';

const WorkspaceBuilder = () => {
  const { id } = useParams();
  const [blocks, setBlocks] = useState([
    { id: 1, type: 'TEXT', content: 'Welcome to your custom workspace! Click add below to build your process.' },
    { id: 2, type: 'CHECKLIST', content: ['Review PRs', 'Drink water'] }
  ]);

  const addBlock = (type) => {
    const newBlock = { id: Date.now(), type, content: type === 'CHECKLIST' ? ['New task'] : '' };
    setBlocks([...blocks, newBlock]);
  };

  const removeBlock = (id) => {
    setBlocks(blocks.filter(b => b.id !== id));
  };

  const addMenuProps = {
    items: [
      { key: 'TEXT', label: 'Text Note', icon: <Type size={16} /> },
      { key: 'CHECKLIST', label: 'Checklist', icon: <CheckSquare size={16} /> },
      { key: 'IMAGE', label: 'Image', icon: <ImageIcon size={16} /> },
      { key: 'VIDEO', label: 'Video Embed', icon: <Video size={16} /> },
    ],
    onClick: (e) => addBlock(e.key)
  };

  return (
    <div className="max-w-4xl mx-auto flex flex-col gap-8 pb-20">
      <div className="border-b border-gray-200 pb-4">
        <Input 
          defaultValue="Untitled Workspace" 
          variant="borderless" 
          className="text-4xl font-bold text-dd-text-main px-0 hover:bg-gray-50 focus:bg-gray-50 rounded-lg transition-colors"
        />
        <p className="text-dd-text-muted mt-2">Build your custom process by adding blocks below.</p>
      </div>

      <div className="flex flex-col gap-4">
        {blocks.map((block) => (
          <div key={block.id} className="group relative flex items-start gap-4 p-4 rounded-xl hover:bg-white hover:shadow-soft transition-all border border-transparent hover:border-gray-100">
            <div className="mt-1 text-gray-300 cursor-grab hover:text-gray-500 opacity-0 group-hover:opacity-100 transition-opacity">
              <GripVertical size={20} />
            </div>
            
            <div className="flex-1">
              {block.type === 'TEXT' && (
                <Input.TextArea 
                  defaultValue={block.content} 
                  autoSize={{ minRows: 2 }}
                  variant="filled"
                  className="bg-gray-50 border-none text-base"
                />
              )}
              {block.type === 'CHECKLIST' && (
                <div className="flex flex-col gap-2">
                  {block.content.map((item, idx) => (
                    <div key={idx} className="flex items-center gap-2">
                      <input type="checkbox" className="w-5 h-5 rounded border-gray-300 text-dd-accent-blue focus:ring-dd-accent-blue" />
                      <Input defaultValue={item} variant="borderless" className="text-base" />
                    </div>
                  ))}
                  <Button type="dashed" className="mt-2 w-max" size="small">+ Add Item</Button>
                </div>
              )}
              {block.type === 'IMAGE' && (
                <div className="h-40 bg-gray-100 rounded-lg flex items-center justify-center border-2 border-dashed border-gray-300 text-gray-400">
                  <div className="flex flex-col items-center">
                    <ImageIcon size={24} className="mb-2" />
                    <span>Upload Image</span>
                  </div>
                </div>
              )}
              {block.type === 'VIDEO' && (
                <div className="h-40 bg-gray-100 rounded-lg flex items-center justify-center border-2 border-dashed border-gray-300 text-gray-400">
                  <div className="flex flex-col items-center">
                    <Video size={24} className="mb-2" />
                    <span>Embed Video URL</span>
                  </div>
                </div>
              )}
            </div>

            <Button 
              type="text" 
              danger 
              icon={<Trash2 size={18} />} 
              onClick={() => removeBlock(block.id)}
              className="opacity-0 group-hover:opacity-100 transition-opacity"
            />
          </div>
        ))}
      </div>

      <div className="flex justify-center mt-4">
        <Dropdown menu={addMenuProps} placement="bottomCenter" arrow>
          <Button type="primary" size="large" icon={<Plus size={20} />} className="shadow-soft hover:shadow-soft-hover">
            Add Block
          </Button>
        </Dropdown>
      </div>
    </div>
  );
};

export default WorkspaceBuilder;
