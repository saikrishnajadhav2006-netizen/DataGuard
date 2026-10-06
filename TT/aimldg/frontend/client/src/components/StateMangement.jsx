import { useState, useEffect } from "react";
import StudentForm from "./StudentForm";
import StudentList from "./StudentList";

const BASE_URL = "http://127.0.0.1:8080/api/v1";

export default function StateMangement() {
  const [students, setStudents] = useState([]);
  const [status, setStatus] = useState("Ready");

  useEffect(() => {
    const fetchStudents = async () => {
      try {
        const res = await fetch(`${BASE_URL}/read`);
        if (!res.ok) throw new Error(`Request failed with status ${res.status}`);
        const response = await res.json();
        setStudents(response.data ?? []);
        setStatus("Records fetched successfully.");
      } catch (err) {
        setStatus("Error fetching data: " + err.message);
      }
    };

    fetchStudents();
  }, []);

  // Save (Create or Update) Logic
  const handleSave = async (formData) => {
    const isUpdate = Boolean(formData.id);
    const url = isUpdate ? `${BASE_URL}/update/${formData.id}` : `${BASE_URL}/create`;
    const method = isUpdate ? "PUT" : "POST";

    try {
      const res = await fetch(url, {
        method,
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({
          name: formData.name,
          email: formData.email,
          ip: formData.ip,
        }),
      });
      const response = await res.json();
      if (!res.ok) throw new Error(response.msg ?? `Request failed with status ${res.status}`);
      setStatus(response.msg ?? "Student saved successfully.");

      const updatedRes = await fetch(`${BASE_URL}/read`);
      if (!updatedRes.ok) throw new Error(`Request failed with status ${updatedRes.status}`);
      const updatedResponse = await updatedRes.json();
      setStudents(updatedResponse.data ?? []);
    } catch (err) {
      setStatus("Save error: " + err.message);
    }
  };

  // Delete Logic
  const handleDelete = async (id) => {
    try {
      const res = await fetch(`${BASE_URL}/delete/${id}`, { method: "DELETE" });
      const response = await res.json();
      if (!res.ok) throw new Error(response.msg ?? `Request failed with status ${res.status}`);
      setStatus(response.msg ?? "Student deleted successfully.");

      const updatedRes = await fetch(`${BASE_URL}/read`);
      if (!updatedRes.ok) throw new Error(`Request failed with status ${updatedRes.status}`);
      const updatedResponse = await updatedRes.json();
      setStudents(updatedResponse.data ?? []);
    } catch (err) {
      setStatus("Delete error: " + err.message);
    }
  };

  return (
    <div style={{ padding: "20px" }}>
      <h2>Student Dashboard</h2>
      <p style={{ fontWeight: "bold", background: "#f0f0f0", padding: "10px" }}>
        Status Log: {status}
      </p>

      <StudentForm onSave={handleSave} />
      <hr />
      <StudentList students={students} onDelete={handleDelete} />
    </div>
  );
}
