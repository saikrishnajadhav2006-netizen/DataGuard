const BASE_API = "http://127.0.0.1:8080/api/v1";

// Utility function to log response messages
function setStatus(message) {
    document.getElementById("output").textContent = message;
}

// 1. READ ALL STUDENTS
async function readData() {
    try {
        let req = await fetch(`${BASE_API}/read`);
        if (!req.ok) throw new Error(`HTTP error! status: ${req.status}`);
        
        let res = await req.json();
        let displayArea = document.getElementById("data-list");
        
        // Extract array from payload object or direct array
        let students = Array.isArray(res) ? res : (res.data || []);

        if (students.length === 0) {
            displayArea.innerHTML = "<p>No student records found.</p>";
            return;
        }

        // Render records in a structured HTML table
        let tableHTML = `
            <table border="1" cellpadding="8" cellspacing="0" style="width: 100%; border-collapse: collapse;">
                <thead>
                    <tr style="background-color: #f2f2f2; text-align: left;">
                        <th>ID</th>
                        <th>Name</th>
                        <th>Email</th>
                        <th>IP Address</th>
                    </tr>
                </thead>
                <tbody>
        `;

        students.forEach(student => {
            tableHTML += `
                <tr>
                    <td>${student.id ?? ''}</td>
                    <td>${student.name ?? ''}</td>
                    <td>${student.email ?? ''}</td>
                    <td>${student.ip ?? ''}</td>
                </tr>
            `;
        });

        tableHTML += `</tbody></table>`;
        displayArea.innerHTML = tableHTML;

        setStatus(res.msg || "Data fetched successfully.");
    } catch (err) {
        setStatus("Error fetching data: " + err.message);
    }
}

// 2. CREATE OR UPDATE STUDENT

// 2. CREATE OR UPDATE STUDENT
async function saveStudent() {
    // Read and trim all inputs
    let idInput = document.getElementById("id").value.trim();
    let name = document.getElementById("name").value.trim();
    let email = document.getElementById("email").value.trim();
    let ip = document.getElementById("ip").value.trim();

    // Ensure required fields are not empty
    if (!name || !email) {
        setStatus("Please fill in both Name and Email.");
        return;
    }

    let studentData = { name, email, ip };

    // Explicit check: If ID is empty, create (POST). Otherwise, update (PUT).
    if (idInput === "" || idInput === null) {
        // CREATE NEW STUDENT (POST)
        try {
            let req = await fetch(`${BASE_API}/create`, {
                method: "POST",
                headers: { 
                    "Content-Type": "application/json",
                    "Accept": "application/json"
                },
                body: JSON.stringify(studentData)
            });

            let resText = await req.text();

            if (!req.ok) {
                setStatus(`Failed to Add (${req.status}): ${resText}`);
                return;
            }

            setStatus("Student added successfully!");
            clearInputs();
            readData(); // Refresh table view
        } catch (err) {
            setStatus("Network error while adding student: " + err.message);
        }
    } else {
        // UPDATE EXISTING STUDENT (PUT)
        try {
            let req = await fetch(`${BASE_API}/update/${idInput}`, {
                method: "PUT",
                headers: { 
                    "Content-Type": "application/json",
                    "Accept": "application/json"
                },
                body: JSON.stringify(studentData)
            });

            let resText = await req.text();

            if (!req.ok) {
                setStatus(`Failed to Update (${req.status}): ${resText}`);
                return;
            }

            setStatus("Student updated successfully!");
            clearInputs();
            readData(); // Refresh table view
        } catch (err) {
            setStatus("Network error while updating student: " + err.message);
        }
    }
}

// Clear Form Input Fields
function clearInputs() {
    document.getElementById("id").value = "";
    document.getElementById("name").value = "";
    document.getElementById("email").value = "";
    document.getElementById("ip").value = "";
}