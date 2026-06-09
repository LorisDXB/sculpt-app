import React, {useEffect, useState} from 'react';
import {
  ActivityIndicator,
  NativeModules,
  Pressable,
  ScrollView,
  StatusBar,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import {SafeAreaView} from 'react-native-safe-area-context';

type SculptSettings = {
  dailyCalorieTarget: number;
  hasApiKey: boolean;
  hasValidatedApiKey: boolean;
  lastValidationMessage: string | null;
};

type SculptSettingsModule = {
  clearApiKey: () => Promise<SculptSettings>;
  clearAllLocalData: () => Promise<SculptSettings>;
  getSettings: () => Promise<SculptSettings>;
  resetToday: () => Promise<SculptSettings>;
  setDailyCalorieTarget: (target: number) => Promise<SculptSettings>;
  validateAndStoreApiKey: (apiKey: string) => Promise<SculptSettings>;
};

const fallbackSettingsModule: SculptSettingsModule = {
  clearApiKey: async () => ({
    dailyCalorieTarget: 2500,
    hasApiKey: false,
    hasValidatedApiKey: false,
    lastValidationMessage: null,
  }),
  clearAllLocalData: async () => ({
    dailyCalorieTarget: 2500,
    hasApiKey: false,
    hasValidatedApiKey: false,
    lastValidationMessage: null,
  }),
  getSettings: async () => ({
    dailyCalorieTarget: 2500,
    hasApiKey: false,
    hasValidatedApiKey: false,
    lastValidationMessage: null,
  }),
  resetToday: async () => ({
    dailyCalorieTarget: 2500,
    hasApiKey: false,
    hasValidatedApiKey: false,
    lastValidationMessage: null,
  }),
  setDailyCalorieTarget: async target => ({
    dailyCalorieTarget: target,
    hasApiKey: false,
    hasValidatedApiKey: false,
    lastValidationMessage: null,
  }),
  validateAndStoreApiKey: async () => ({
    dailyCalorieTarget: 2500,
    hasApiKey: true,
    hasValidatedApiKey: true,
    lastValidationMessage: 'API key validated.',
  }),
};

const sculptSettings = (NativeModules.SculptSettings ?? fallbackSettingsModule) as SculptSettingsModule;

function App(): React.JSX.Element {
  const [settings, setSettings] = useState<SculptSettings | null>(null);
  const [apiKey, setApiKey] = useState('');
  const [dailyTarget, setDailyTarget] = useState('2500');
  const [isLoading, setIsLoading] = useState(true);
  const [isSavingKey, setIsSavingKey] = useState(false);
  const [isSavingTarget, setIsSavingTarget] = useState(false);
  const [isRunningMaintenance, setIsRunningMaintenance] = useState(false);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);

  useEffect(() => {
    loadSettings();
  }, []);

  const loadSettings = async () => {
    try {
      const nextSettings = await sculptSettings.getSettings();
      setSettings(nextSettings);
      setDailyTarget(String(nextSettings.dailyCalorieTarget));
      setStatusMessage(nextSettings.lastValidationMessage);
    } catch {
      setStatusMessage('Could not load settings.');
    } finally {
      setIsLoading(false);
    }
  };

  const saveApiKey = async () => {
    setIsSavingKey(true);
    setStatusMessage(null);

    try {
      const nextSettings = await sculptSettings.validateAndStoreApiKey(apiKey);
      setSettings(nextSettings);
      setApiKey('');
      setStatusMessage(nextSettings.lastValidationMessage ?? 'API key validated.');
    } catch (error: unknown) {
      const message = error instanceof Error ? error.message : 'Could not validate API key.';
      setStatusMessage(message);
      await loadSettings();
    } finally {
      setIsSavingKey(false);
    }
  };

  const saveDailyTarget = async () => {
    const parsedTarget = Number(dailyTarget);
    if (!Number.isFinite(parsedTarget) || parsedTarget <= 0) {
      setStatusMessage('Daily target must be a positive number.');
      return;
    }

    setIsSavingTarget(true);
    setStatusMessage(null);

    try {
      const nextSettings = await sculptSettings.setDailyCalorieTarget(parsedTarget);
      setSettings(nextSettings);
      setDailyTarget(String(nextSettings.dailyCalorieTarget));
      setStatusMessage('Daily calorie target updated.');
    } catch {
      setStatusMessage('Could not update daily target.');
    } finally {
      setIsSavingTarget(false);
    }
  };

  const clearApiKey = async () => {
    setStatusMessage(null);

    try {
      const nextSettings = await sculptSettings.clearApiKey();
      setSettings(nextSettings);
      setApiKey('');
      setStatusMessage('API key cleared.');
    } catch {
      setStatusMessage('Could not clear API key.');
    }
  };

  const resetToday = async () => {
    setIsRunningMaintenance(true);
    setStatusMessage(null);

    try {
      const nextSettings = await sculptSettings.resetToday();
      setSettings(nextSettings);
      setStatusMessage('Today was reset.');
    } catch {
      setStatusMessage('Could not reset today.');
    } finally {
      setIsRunningMaintenance(false);
    }
  };

  const clearAllLocalData = async () => {
    setIsRunningMaintenance(true);
    setStatusMessage(null);

    try {
      const nextSettings = await sculptSettings.clearAllLocalData();
      setSettings(nextSettings);
      setApiKey('');
      setDailyTarget(String(nextSettings.dailyCalorieTarget));
      setStatusMessage('All local data was cleared.');
    } catch {
      setStatusMessage('Could not clear local data.');
    } finally {
      setIsRunningMaintenance(false);
    }
  };

  if (isLoading) {
    return (
      <SafeAreaView style={styles.loadingScreen}>
        <StatusBar barStyle="light-content" backgroundColor="#09111e" />
        <ActivityIndicator color="#f59e0b" size="large" />
      </SafeAreaView>
    );
  }

  const requiresKey = !settings?.hasValidatedApiKey;

  return (
    <SafeAreaView style={styles.screen}>
      <StatusBar barStyle="light-content" backgroundColor="#09111e" />
      <ScrollView contentContainerStyle={styles.content}>
        <View style={styles.hero}>
          <Text style={styles.eyebrow}>Sculpt control panel</Text>
          <Text style={styles.title}>
            {requiresKey
              ? 'Validate your API key to unlock Add meal.'
              : 'Widget-first meal logging is ready.'}
          </Text>
          <Text style={styles.body}>
            {requiresKey
              ? 'The widget can still show and edit values, but photo-based meal logging stays blocked until your OpenAI key is validated.'
              : 'Your OpenAI key is validated. Use this screen only for quick settings and fallback configuration.'}
          </Text>
        </View>

        <View style={styles.card}>
          <Text style={styles.cardTitle}>AI key</Text>
          <Text style={styles.cardBody}>
            {settings?.hasValidatedApiKey
              ? 'Validated and ready for meal capture.'
              : 'Paste your OpenAI API key and validate it before using Add meal.'}
          </Text>
          <TextInput
            autoCapitalize="none"
            autoCorrect={false}
            onChangeText={setApiKey}
            placeholder="sk-..."
            placeholderTextColor="#7b8a9c"
            secureTextEntry
            style={styles.input}
            value={apiKey}
          />
          <Pressable
            disabled={isSavingKey || apiKey.trim().length === 0}
            onPress={saveApiKey}
            style={({pressed}) => [
              styles.primaryButton,
              (pressed || isSavingKey || apiKey.trim().length === 0) && styles.buttonPressed,
            ]}>
            {isSavingKey ? (
              <ActivityIndicator color="#ffffff" />
            ) : (
              <Text style={styles.primaryButtonText}>Validate API key</Text>
            )}
          </Pressable>
          {settings?.hasApiKey ? (
            <Pressable
              onPress={clearApiKey}
              style={({pressed}) => [styles.secondaryButton, pressed && styles.buttonPressed]}>
              <Text style={styles.secondaryButtonText}>Clear saved key</Text>
            </Pressable>
          ) : null}
        </View>

        <View style={styles.card}>
          <Text style={styles.cardTitle}>Daily target</Text>
          <Text style={styles.cardBody}>
            This updates the widget target directly. The current saved value is{' '}
            <Text style={styles.inlineHighlight}>{settings?.dailyCalorieTarget ?? 0} kcal</Text>.
          </Text>
          <TextInput
            keyboardType="number-pad"
            onChangeText={setDailyTarget}
            placeholder="2500"
            placeholderTextColor="#7b8a9c"
            style={styles.input}
            value={dailyTarget}
          />
          <Pressable
            disabled={isSavingTarget}
            onPress={saveDailyTarget}
            style={({pressed}) => [
              styles.primaryButton,
              (pressed || isSavingTarget) && styles.buttonPressed,
            ]}>
            {isSavingTarget ? (
              <ActivityIndicator color="#ffffff" />
            ) : (
              <Text style={styles.primaryButtonText}>Save daily target</Text>
            )}
          </Pressable>
        </View>

        <View style={styles.card}>
          <Text style={styles.cardTitle}>Current status</Text>
          <Text style={styles.cardBody}>
            Widget capture gate:{' '}
            <Text style={styles.inlineHighlight}>
              {settings?.hasValidatedApiKey ? 'unlocked' : 'locked'}
            </Text>
          </Text>
          <Text style={styles.cardBody}>
            Validation state:{' '}
            <Text style={styles.inlineHighlight}>
              {settings?.hasValidatedApiKey
                ? 'validated'
                : settings?.hasApiKey
                  ? 'saved but invalid'
                  : 'missing'}
            </Text>
          </Text>
          {statusMessage ? <Text style={styles.statusMessage}>{statusMessage}</Text> : null}
        </View>

        <View style={styles.card}>
          <Text style={styles.cardTitle}>Maintenance</Text>
          <Text style={styles.cardBody}>
            Use these only when you want to reset the day or wipe local state during testing.
          </Text>
          <Pressable
            disabled={isRunningMaintenance}
            onPress={resetToday}
            style={({pressed}) => [
              styles.secondaryButton,
              (pressed || isRunningMaintenance) && styles.buttonPressed,
            ]}>
            {isRunningMaintenance ? (
              <ActivityIndicator color="#d6dfeb" />
            ) : (
              <Text style={styles.secondaryButtonText}>Reset today</Text>
            )}
          </Pressable>
          <Pressable
            disabled={isRunningMaintenance}
            onPress={clearAllLocalData}
            style={({pressed}) => [
              styles.dangerButton,
              (pressed || isRunningMaintenance) && styles.buttonPressed,
            ]}>
            <Text style={styles.dangerButtonText}>Clear all local data</Text>
          </Pressable>
        </View>
      </ScrollView>
    </SafeAreaView>
  );
}

const styles = StyleSheet.create({
  loadingScreen: {
    alignItems: 'center',
    backgroundColor: '#09111e',
    flex: 1,
    justifyContent: 'center',
  },
  screen: {
    backgroundColor: '#09111e',
    flex: 1,
  },
  content: {
    paddingBottom: 32,
    paddingHorizontal: 20,
    paddingTop: 20,
  },
  hero: {
    marginBottom: 20,
  },
  eyebrow: {
    color: '#f59e0b',
    fontSize: 13,
    fontWeight: '700',
    letterSpacing: 1,
    marginBottom: 10,
    textTransform: 'uppercase',
  },
  title: {
    color: '#f8fafc',
    fontSize: 30,
    fontWeight: '800',
    lineHeight: 36,
    marginBottom: 10,
  },
  body: {
    color: '#c1ccda',
    fontSize: 16,
    lineHeight: 24,
  },
  card: {
    backgroundColor: '#132033',
    borderColor: '#26384f',
    borderRadius: 24,
    borderWidth: 1,
    marginTop: 14,
    padding: 18,
  },
  cardTitle: {
    color: '#f8fafc',
    fontSize: 19,
    fontWeight: '700',
    marginBottom: 8,
  },
  cardBody: {
    color: '#c1ccda',
    fontSize: 15,
    lineHeight: 22,
    marginBottom: 12,
  },
  input: {
    backgroundColor: '#0a1526',
    borderColor: '#304662',
    borderRadius: 16,
    borderWidth: 1,
    color: '#f8fafc',
    fontSize: 15,
    marginBottom: 12,
    paddingHorizontal: 14,
    paddingVertical: 12,
  },
  primaryButton: {
    alignItems: 'center',
    backgroundColor: '#d97706',
    borderRadius: 16,
    justifyContent: 'center',
    minHeight: 48,
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  secondaryButton: {
    alignItems: 'center',
    borderColor: '#42556f',
    borderRadius: 16,
    borderWidth: 1,
    justifyContent: 'center',
    marginTop: 10,
    minHeight: 48,
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  dangerButton: {
    alignItems: 'center',
    backgroundColor: '#7f1d1d',
    borderRadius: 16,
    justifyContent: 'center',
    marginTop: 10,
    minHeight: 48,
    paddingHorizontal: 16,
    paddingVertical: 12,
  },
  buttonPressed: {
    opacity: 0.7,
  },
  primaryButtonText: {
    color: '#fff7ed',
    fontSize: 15,
    fontWeight: '700',
  },
  secondaryButtonText: {
    color: '#d6dfeb',
    fontSize: 15,
    fontWeight: '600',
  },
  dangerButtonText: {
    color: '#fee2e2',
    fontSize: 15,
    fontWeight: '700',
  },
  inlineHighlight: {
    color: '#f8fafc',
    fontWeight: '700',
  },
  statusMessage: {
    color: '#f59e0b',
    fontSize: 14,
    lineHeight: 20,
    marginTop: 4,
  },
});

export default App;
