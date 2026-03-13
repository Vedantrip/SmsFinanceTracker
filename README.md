# 🚀 SmsFinanceTracker

> 📊 AI-powered Android app that reads SMS transactions, detects subscriptions, and provides real-time financial insights.

---

## 📱 Overview

SmsFinanceTracker is a native Android fintech application that:

* 📩 Reads bank transaction SMS automatically
* 💳 Detects debit & credit transactions
* 🔁 Identifies recurring subscriptions
* 📈 Calculates monthly spending
* 🥧 Displays category breakdown with charts
* 🧠 Generates smart spending insights

Built completely using **Kotlin + Room + Android Native UI**.

---

## ✨ Features

### 💰 Monthly Spend Dashboard

* Real-time monthly debit calculation
* Category grouping (Food, Entertainment, Transport, Wallet, Other)
* Beautiful donut-style pie chart

### 🔍 Subscription Detection

* Detects recurring payments
* Identifies monthly & yearly subscriptions
* Finds patterns using interval analysis

### 📦 Transaction Storage

* Uses Room Database
* Stores merchant, amount, type, timestamp
* Offline-first architecture

### 📊 Insights Engine

* Category-level breakdown
* Spending trends
* Recent transactions list

---

## 🏗 Architecture

Clean separation of concerns:

```
MainActivity → Hosts DashboardFragment
DashboardFragment → UI + Dashboard Logic
Room Database → Persistent storage
TransactionEntity → Data model
TransactionDao → DB operations
```

* MVVM-friendly structure
* Coroutine-based background processing
* Fragment-based scalable UI

---

## 🛠 Tech Stack

* Kotlin
* Android SDK
* Room Database
* MPAndroidChart
* Coroutines
* Material Design

---

## Previews :
<img width="398" height="288" alt="image" src="https://github.com/user-attachments/assets/18d4125d-4faa-460b-b4fc-c24ed8c5412c" />
<img width="389" height="596" alt="image" src="https://github.com/user-attachments/assets/9c5aaeab-06c4-4598-b644-2831a289fc8d" />
<img width="380" height="789" alt="image" src="https://github.com/user-attachments/assets/4321f839-ae82-49f2-95ea-562cf1c34f5e" />
<img width="392" height="710" alt="image" src="https://github.com/user-attachments/assets/a96ce286-5ec7-4bd3-8911-34f64f92704a" />



## 🔐 Permissions Used

```xml
READ_SMS
```

Used only to:

* Detect financial transactions
* Analyze spending patterns

No data is sent to any server.
Fully local processing.

---

## 🚀 Future Improvements

* Smart savings recommendations
* Spending trend graphs
* AI anomaly detection
* Export reports (PDF/CSV)
* Budget alerts
* Bank classification engine

---

## 👨‍💻 Author

Vedant Tripathi
Computer Science & Communication Engineering
Fintech & Systems Engineering Enthusiast

---

## ⭐ Why This Project Matters

This is not just an Android app —
It demonstrates:

* Real-world data parsing
* Pattern detection algorithms
* Database architecture
* UI/UX system design
* Financial analytics logic

A production-ready foundation for a personal finance platform.

---
