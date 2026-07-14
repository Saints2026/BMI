package com.bmi.model.ai;

import java.util.List;

public class AiRequest {
    private String systemPrompt;
    private UserMetrics userMetrics;
    private HistoryTrend historyTrend;
    private ModelParams modelParams;

    public static class UserMetrics {
        private double bmi;
        private String bmiGrade;
        private double bodyFat;
        private double weight;
        private double height;
        private int age;
        private int gender;
        private String measureTime;

        public double getBmi() { return bmi; }
        public void setBmi(double bmi) { this.bmi = bmi; }
        public String getBmiGrade() { return bmiGrade; }
        public void setBmiGrade(String bmiGrade) { this.bmiGrade = bmiGrade; }
        public double getBodyFat() { return bodyFat; }
        public void setBodyFat(double bodyFat) { this.bodyFat = bodyFat; }
        public double getWeight() { return weight; }
        public void setWeight(double weight) { this.weight = weight; }
        public double getHeight() { return height; }
        public void setHeight(double height) { this.height = height; }
        public int getAge() { return age; }
        public void setAge(int age) { this.age = age; }
        public int getGender() { return gender; }
        public void setGender(int gender) { this.gender = gender; }
        public String getMeasureTime() { return measureTime; }
        public void setMeasureTime(String measureTime) { this.measureTime = measureTime; }
    }

    public static class HistoryTrend {
        private int count;
        private String direction;
        private List<Point> points;

        public static class Point {
            private String measureTime;
            private double bmi;
            private double weight;
            private double bodyFat;
            public String getMeasureTime() { return measureTime; }
            public void setMeasureTime(String measureTime) { this.measureTime = measureTime; }
            public double getBmi() { return bmi; }
            public void setBmi(double bmi) { this.bmi = bmi; }
            public double getWeight() { return weight; }
            public void setWeight(double weight) { this.weight = weight; }
            public double getBodyFat() { return bodyFat; }
            public void setBodyFat(double bodyFat) { this.bodyFat = bodyFat; }
        }

        public int getCount() { return count; }
        public void setCount(int count) { this.count = count; }
        public String getDirection() { return direction; }
        public void setDirection(String direction) { this.direction = direction; }
        public List<Point> getPoints() { return points; }
        public void setPoints(List<Point> points) { this.points = points; }
    }

    public static class ModelParams {
        private String model;
        private double temperature;
        private int maxTokens;

        public String getModel() { return model; }
        public void setModel(String model) { this.model = model; }
        public double getTemperature() { return temperature; }
        public void setTemperature(double temperature) { this.temperature = temperature; }
        public int getMaxTokens() { return maxTokens; }
        public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }
    }

    public String getSystemPrompt() { return systemPrompt; }
    public void setSystemPrompt(String systemPrompt) { this.systemPrompt = systemPrompt; }
    public UserMetrics getUserMetrics() { return userMetrics; }
    public void setUserMetrics(UserMetrics userMetrics) { this.userMetrics = userMetrics; }
    public HistoryTrend getHistoryTrend() { return historyTrend; }
    public void setHistoryTrend(HistoryTrend historyTrend) { this.historyTrend = historyTrend; }
    public ModelParams getModelParams() { return modelParams; }
    public void setModelParams(ModelParams modelParams) { this.modelParams = modelParams; }

    public boolean isValid() {
        return userMetrics != null
                && userMetrics.getHeight() > 0
                && userMetrics.getWeight() > 0
                && userMetrics.getAge() >= 1
                && (userMetrics.getGender() == 0 || userMetrics.getGender() == 1)
                && modelParams != null
                && modelParams.getMaxTokens() > 0;
    }
}
