import { useState } from "react";

export default function StudentForm({ onSave }) {
  const [formData, setFormData] = useState({ id: "", name: "", email: "", ip: "" });

  const handleChange = (e) => {
    setFormData({ ...formData, [e.target.name]: e.target.value });
  };

  const handleSubmit = (e) => {
    e.preventDefault();
    onSave(formData);
    setFormData({ id: "", name: "", email: "", ip: "" });
  };

  return (
    <form onSubmit={handleSubmit} style={{ marginBottom: "20px" }}>
      <h3>Create / Update Student</h3>
      <input
        type="number"
        name="id"
        placeholder="Student ID (leave blank for Create)"
        value={formData.id}
        onChange={handleChange}
      />
      <br />
      <input
        type="text"
        name="name"
        placeholder="Name"
        value={formData.name}
        onChange={handleChange}
        required
      />
      <br />
      <input
        type="email"
        name="email"
        placeholder="Email"
        value={formData.email}
        onChange={handleChange}
        required
      />
      <br />
      <input
        type="text"
        name="ip"
        placeholder="IP Address"
        value={formData.ip}
        onChange={handleChange}
        required
      />
      <br />
      <button type="submit">Save Student</button>
    </form>
  );
}
