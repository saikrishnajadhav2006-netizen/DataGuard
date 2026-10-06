// Simple script to seed student data into the API
// Usage: node seed_students.js

const fs = require("fs");

const BASE_URL = "http://localhost:8080/api/v1/create"; // change if needed
const FILE_PATH = "data.json"; // change if your file is named differently

async function main() {
  // 1. Read the JSON file
  const file = fs.readFileSync(FILE_PATH, "utf-8");
  const students = JSON.parse(file).data;

  console.log(`Found ${students.length} students. Sending them one by one...\n`);

  // 2. Loop through each student and POST it
  for (const student of students) {
    try {
      const response = await fetch(BASE_URL, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(student),
      });

      if (response.ok) {
        console.log(`✅ Added: ${student.name} (${student.email})`);
      } else {
        console.log(`❌ Failed: ${student.name} - Status ${response.status}`);
      }
    } catch (error) {
      console.log(`❌ Error for ${student.name}: ${error.message}`);
    }
  }

  console.log("\nDone!");
}

main();