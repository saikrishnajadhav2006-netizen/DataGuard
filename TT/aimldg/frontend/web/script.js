const BASE_API = "http://127.0.0.1:8080/api/v1";

// Utility function to log response messages
function setStatus(message) {
    document.getElementById("output").textContent = message;
}

// 1. READ ALL STUDENTS
async function readData() {
    try {
        let req = await fetch(`${BASE_API}/read`);
        let res = await req.text();
        document.getElementById("data-list").textContent = res;
        setStatus("Data fetched successfully.");
    } catch (err) {
        setStatus("Error fetching data: " + err.message);
    }
}

// 2. CREATE OR UPDATE STUDENT
async function saveStudent() {
    let id = document.getElementById("id").value.trim();
    let name = document.getElementById("name").value;
    let email = document.getElementById("email").value;
    let ip = document.getElementById("ip").value;

    let studentData = { name, email, ip };

    if (id === "") {
        // CREATE (POST)
        try {
            let req = await fetch(`${BASE_API}/create`, {
                method: "POST",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(studentData)
            });
            let res = await req.text();
            setStatus(res);
            clearInputs();
            readData();
        } catch (err) {
            setStatus("Error creating record: " + err.message);
        }
    } else {
        // UPDATE (PUT)
        try {
            let req = await fetch(`${BASE_API}/update/${id}`, {
                method: "PUT",
                headers: { "Content-Type": "application/json" },
                body: JSON.stringify(studentData)
            });
            let res = await req.text();
            setStatus(res);
            clearInputs();
            readData();
        } catch (err) {
            setStatus("Error updating record: " + err.message);
        }
    }
}

// 3. DELETE STUDENT
async function deleteData() {
    let id = document.getElementById("delete-id").value.trim();
    if (!id) {
        setStatus("Please provide a valid ID to delete.");
        return;
    }

    try {
        let req = await fetch(`${BASE_API}/delete/${id}`, {
            method: "DELETE"
        });
        let res = await req.text();
        setStatus(res);
        document.getElementById("delete-id").value = "";
        readData();
    } catch (err) {
        setStatus("Error deleting record: " + err.message);
    }
}

// Clear Form Input Fields
function clearInputs() {
    document.getElementById("id").value = "";
    document.getElementById("name").value = "";
    document.getElementById("email").value = "";
    document.getElementById("ip").value = "";
}