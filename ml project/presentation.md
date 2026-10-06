# Google Analytics Customer Revenue Prediction

---

## Introduction

- **Objective**: Predict customer revenue from Google Analytics data
- **Dataset**: Google Analytics data with user sessions
- **Approach**: Machine Learning classification and regression
- **Tools**: Python, Pandas, LightGBM, Streamlit

---

## Data Overview

- Source: Google Analytics train_v2.csv
- Sample: 300,000 rows
- Features: Device info, geo network, totals, traffic source
- Target: Revenue prediction (binary classification + regression)

---

## Data Preprocessing

- Parse JSON columns: device, geoNetwork, totals, trafficSource
- Normalize nested data into flat columns
- Drop noisy/unuseful columns (e.g., browserVersion, networkDomain)
- Convert data types (hits, pageviews to numeric)
- Handle missing values

---

## Exploratory Data Analysis

- Visualize distributions of key features
- Analyze correlations
- Identify patterns in user behavior
- Plots: Histograms, bar charts, heatmaps (refer to notebook visualizations)

---

## Feature Engineering

- Encode categorical variables
- Create derived features if needed
- Select important features for modeling

---

## Modeling Approach

- **Step 1**: Binary Classification - Predict if user will make a purchase
  - Model: LightGBM Classifier
  - Handle class imbalance with class_weight='balanced'
- **Step 2**: Regression - Predict revenue amount for purchasers
  - Model: LightGBM Regressor (assuming from context)

---

## Model Training & Evaluation

- Train-test split
- Hyperparameter tuning with GridSearchCV
- Metrics: F1-score for classification, RMSE/MSE for regression
- Cross-validation for robustness

---

## Results

- Classification Report: Precision, Recall, F1-score
- Best parameters from grid search
- Cross-validation scores
- Model performance metrics

---

## Deployment

- Built a Streamlit web app for predictions
- User inputs session features
- App predicts purchase likelihood and estimated revenue
- Interactive interface for real-time predictions

---

## Conclusion

- Successfully built ML pipeline for revenue prediction
- Achieved good performance with LightGBM
- Deployed as user-friendly web app
- Future improvements: More features, advanced models, real-time data

---

## Thank You

Questions?