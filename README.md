# MediSort Medicine Management App | Mobile App Development
[Jan 2025 - March 2025]

## Brief Description
A mobile application designed to streamline medication management by allowing users to sort, track, and receive reminders for their prescriptions, improving adherence and reducing errors.

## Contributions
Implemented the camera and image capture function for medication records, enabling users to upload photos of prescriptions and packaging. 

Developed the database integration for image storage, ensuring secure and efficient retrieval of medication records. 

Designed and implemented the bottom navigation bar for seamless app navigation. 

Contributed to the UI/UX design, enhancing usability and user experience.

## Lessons Learnt
Strengthened skills in mobile app development, particularly camera APIs, UI components, and database integration.

__________________________________________________________________________________________________________________________________________

# MediSort - A Mobile Medication Management App

MediSort is a comprehensive mobile healthcare application designed to improve medication adherence and management for individuals who require reliable medication scheduling. The app provides intuitive medication tracking, personalized reminders, and adherence monitoring to help users maintain their medication regimens effectively.

Video Demo: https://www.youtube.com/watch?v=eGnqcPkD6FY 

## About

MediSort was developed as a group project for the INF2007 Mobile Application Development course at Singapore Institute of Technology. The application leverages modern mobile technologies to address real-world health challenges, focusing on medication management, reminders, and adherence tracking.

## Key Features

### Personalized Medication Records
- Create detailed medication entries with custom information:
  - Medication name
  - Treatment purpose
  - Dosage instructions
  - Custom notes
- Attach photos of medications to help with identification
- Maintain a comprehensive database of current medications
- User-friendly interface for easy medication management

### Smart Medication Reminders
- Set personalized reminder schedules for each medication
- Configure reminders for specific days of the week
- Persistent alarm-style notifications that require acknowledgment
- Options to mark medications as taken or snooze reminders
- Ability to pause/resume medication reminders when needed

### Medication Identification
- OCR (Optical Character Recognition) technology to scan and identify medications
- Extract medication information from scanned text
- Store and organize medication details automatically

### Adherence Tracking & Gamification
- Track medication adherence history with calendar visualization
- Color-coded adherence calendar showing:
  - All medications taken (green)
  - Partially taken (orange)
  - Missed doses (red)
- Streak counter to gamify consistent medication taking
- Record of longest adherence streak to encourage long-term compliance

### User Account Management
- Secure authentication with Firebase
- Cloud-based data storage for access across devices
- Privacy-focused design for sensitive health information

## Technical Implementation

MediSort is built using modern Android development technologies:

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose
- **Architecture**: MVVM (Model-View-ViewModel)
- **Backend/Database**: Firebase Authentication & Firestore
- **Storage**: Firebase Storage for medication images
- **Notifications**: Firebase Cloud Messaging
- **Computer Vision**: MLKit for Text Recognition and QR scanning
- **Camera**: CameraX for medication photos and scanning
- **Local Storage**: Data persistence for offline access

## Project Structure

The project follows a modular structure:

- **screens/**: UI screens for different app features
- **components/**: Reusable UI components
- **utils/**: Utility classes and helper functions
- **models/**: Data classes representing app entities

## Security & Privacy

MediSort implements several security measures:

- Firebase Authentication for secure user login
- Firestore security rules to protect user data
- Local storage of sensitive medication information
- Permission handling for camera, storage, and notifications

## Mobile Aspects Integration

This project integrates several key mobile technologies:

1. **Mobile Communication**:
   - Firebase Cloud Messaging for real-time notifications
   - Push notification reminders with adherence tracking

2. **Mobile Multimedia**:
   - Camera integration for medication photos
   - Image processing for medication identification
   - Visual dashboard for adherence statistics

3. **Mobile Computing**:
   - On-device data processing
   - Local storage optimization
   - Background processing for reminders

## Getting Started

### Prerequisites
- Android Studio Arctic Fox or later
- JDK 11 or higher
- Android SDK 24+
- Google services configuration for Firebase

### Setup
1. Clone the repository
2. Open the project in Android Studio
3. Sync Gradle dependencies
4. Build and run the application

## Contributors

This project was developed by:
- Dominic Loh Rui Jie (ID: 2301823)
- Glenn Tham Guoxiang (ID: 2301803)
- Chin Hui Min (ID: 2301941)
- Khoo Ye Chen (ID: 2301821)
- Chua Shing Ying (ID: 2301932)
