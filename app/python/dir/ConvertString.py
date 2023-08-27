import pandas as pd
import joblib


class ConvertString:
    def __init__(self, file):
        self.data_test = pd.DataFrame()
        self.loaded_model = joblib.load(file)
        self.data = None


    def setString(self, string):
        start_index = string.find('{')
        end_index = string.rfind('}')
        self.data = string[start_index:end_index + 1]

        return string[start_index:end_index + 1]

    def updateData(self):
        self.data_test = self.data


    def getResult(self):
        new_predictions = self.loaded_model.predict(self.data_test)

        return new_predictions
