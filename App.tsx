import React, {useEffect, useState} from 'react';
import {
  ActivityIndicator,
  NativeModules,
  PermissionsAndroid,
  Pressable,
  Platform,
  ScrollView,
  StatusBar,
  StyleSheet,
  Text,
  TextInput,
  View,
} from 'react-native';
import {SafeAreaView} from 'react-native-safe-area-context';

type SculptSettings = {
  caloriesConsumedToday: number;
  dailyCalorieTarget: number;
  defaultWeight: number;
  hasApiKey: boolean;
  hasValidatedApiKey: boolean;
  lastSuccessfulStepRefreshAtMillis: number;
  lastValidationMessage: string | null;
  nextStepRefreshAtMillis: number;
  stepStatus: string;
  stepPollingSeconds: number;
  stepSensorAvailable: boolean;
  stepsPermissionGranted: boolean;
  todaySteps: number;
};

type SculptSettingsModule = {
  clearApiKey: () => Promise<SculptSettings>;
  clearAllLocalData: () => Promise<SculptSettings>;
  getSettings: () => Promise<SculptSettings>;
  refreshSteps: () => Promise<SculptSettings>;
  resetToday: () => Promise<SculptSettings>;
  setDailyCalorieTarget: (target: number) => Promise<SculptSettings>;
  setDefaultWeight: (weight: number) => Promise<SculptSettings>;
  setStepPollingMinutes: (minutes: number) => Promise<SculptSettings>;
  validateAndStoreApiKey: (apiKey: string) => Promise<SculptSettings>;
};

const fallbackSettingsModule: SculptSettingsModule = {
  clearApiKey: async () => ({
    caloriesConsumedToday: 0,
    dailyCalorieTarget: 2500,
    defaultWeight: 70,
    hasApiKey: false,
    hasValidatedApiKey: false,
    lastSuccessfulStepRefreshAtMillis: 0,
    lastValidationMessage: null,
    nextStepRefreshAtMillis: 0,
    stepStatus: 'BASELINE_PENDING',
    stepPollingSeconds: 1800,
    stepSensorAvailable: true,
    stepsPermissionGranted: true,
    todaySteps: 0,
  }),
  clearAllLocalData: async () => ({
    caloriesConsumedToday: 0,
    dailyCalorieTarget: 2500,
    defaultWeight: 70,
    hasApiKey: false,
    hasValidatedApiKey: false,
    lastSuccessfulStepRefreshAtMillis: 0,
    lastValidationMessage: null,
    nextStepRefreshAtMillis: 0,
    stepStatus: 'BASELINE_PENDING',
    stepPollingSeconds: 1800,
    stepSensorAvailable: true,
    stepsPermissionGranted: true,
    todaySteps: 0,
  }),
  getSettings: async () => ({
    caloriesConsumedToday: 0,
    dailyCalorieTarget: 2500,
    defaultWeight: 70,
    hasApiKey: false,
    hasValidatedApiKey: false,
    lastSuccessfulStepRefreshAtMillis: 0,
    lastValidationMessage: null,
    nextStepRefreshAtMillis: 0,
    stepStatus: 'BASELINE_PENDING',
    stepPollingSeconds: 1800,
    stepSensorAvailable: true,
    stepsPermissionGranted: true,
    todaySteps: 0,
  }),
  refreshSteps: async () => ({
    caloriesConsumedToday: 0,
    dailyCalorieTarget: 2500,
    defaultWeight: 70,
    hasApiKey: false,
    hasValidatedApiKey: false,
    lastSuccessfulStepRefreshAtMillis: 0,
    lastValidationMessage: null,
    nextStepRefreshAtMillis: 0,
    stepStatus: 'BASELINE_PENDING',
    stepPollingSeconds: 1800,
    stepSensorAvailable: true,
    stepsPermissionGranted: true,
    todaySteps: 0,
  }),
  resetToday: async () => ({
    caloriesConsumedToday: 0,
    dailyCalorieTarget: 2500,
    defaultWeight: 70,
    hasApiKey: false,
    hasValidatedApiKey: false,
    lastSuccessfulStepRefreshAtMillis: 0,
    lastValidationMessage: null,
    nextStepRefreshAtMillis: 0,
    stepStatus: 'BASELINE_PENDING',
    stepPollingSeconds: 1800,
    stepSensorAvailable: true,
    stepsPermissionGranted: true,
    todaySteps: 0,
  }),
  setDailyCalorieTarget: async target => ({
    caloriesConsumedToday: 0,
    dailyCalorieTarget: target,
    defaultWeight: 70,
    hasApiKey: false,
    hasValidatedApiKey: false,
    lastSuccessfulStepRefreshAtMillis: 0,
    lastValidationMessage: null,
    nextStepRefreshAtMillis: 0,
    stepStatus: 'BASELINE_PENDING',
    stepPollingSeconds: 1800,
    stepSensorAvailable: true,
    stepsPermissionGranted: true,
    todaySteps: 0,
  }),
  setDefaultWeight: async weight => ({
    caloriesConsumedToday: 0,
    dailyCalorieTarget: 2500,
    defaultWeight: weight,
    hasApiKey: false,
    hasValidatedApiKey: false,
    lastSuccessfulStepRefreshAtMillis: 0,
    lastValidationMessage: null,
    nextStepRefreshAtMillis: 0,
    stepStatus: 'BASELINE_PENDING',
    stepPollingSeconds: 1800,
    stepSensorAvailable: true,
    stepsPermissionGranted: true,
    todaySteps: 0,
  }),
  setStepPollingMinutes: async seconds => ({
    caloriesConsumedToday: 0,
    dailyCalorieTarget: 2500,
    defaultWeight: 70,
    hasApiKey: false,
    hasValidatedApiKey: false,
    lastSuccessfulStepRefreshAtMillis: Date.now(),
    lastValidationMessage: null,
    nextStepRefreshAtMillis: Date.now() + seconds * 1000,
    stepStatus: 'READY',
    stepPollingSeconds: seconds,
    stepSensorAvailable: true,
    stepsPermissionGranted: true,
    todaySteps: 0,
  }),
  validateAndStoreApiKey: async () => ({
    caloriesConsumedToday: 0,
    dailyCalorieTarget: 2500,
    defaultWeight: 70,
    hasApiKey: true,
    hasValidatedApiKey: true,
    lastSuccessfulStepRefreshAtMillis: 0,
    lastValidationMessage: 'API key validated.',
    nextStepRefreshAtMillis: 0,
    stepStatus: 'BASELINE_PENDING',
    stepPollingSeconds: 1800,
    stepSensorAvailable: true,
    stepsPermissionGranted: true,
    todaySteps: 0,
  }),
};

const sculptSettings = (NativeModules.SculptSettings ?? fallbackSettingsModule) as SculptSettingsModule;

function App(): React.JSX.Element {
  const [settings, setSettings] = useState<SculptSettings | null>(null);
  const [apiKey, setApiKey] = useState('');
  const [dailyTarget, setDailyTarget] = useState('2500');
  const [defaultWeight, setDefaultWeight] = useState('70.0');
  const [isLoading, setIsLoading] = useState(true);
  const [isSavingKey, setIsSavingKey] = useState(false);
  const [isSavingTarget, setIsSavingTarget] = useState(false);
  const [isSavingDefaultWeight, setIsSavingDefaultWeight] = useState(false);
  const [isSavingStepPolling, setIsSavingStepPolling] = useState(false);
  const [isRefreshingSteps, setIsRefreshingSteps] = useState(false);
  const [isRunningMaintenance, setIsRunningMaintenance] = useState(false);
  const [statusMessage, setStatusMessage] = useState<string | null>(null);
  const [nowMillis, setNowMillis] = useState(Date.now());

  useEffect(() => {
    loadSettings();
  }, []);

  useEffect(() => {
    const interval = setInterval(() => {
      setNowMillis(Date.now());
    }, 1000);
    return () => clearInterval(interval);
  }, []);

  useEffect(() => {
    if (!settings?.nextStepRefreshAtMillis) {
      return;
    }

    if (settings.nextStepRefreshAtMillis > nowMillis) {
      return;
    }

    const timeout = setTimeout(() => {
      loadSettings();
    }, 1500);

    return () => clearTimeout(timeout);
  }, [nowMillis, settings?.nextStepRefreshAtMillis]);

  const loadSettings = async () => {
    try {
      const nextSettings = await sculptSettings.getSettings();
      setSettings(nextSettings);
      setDailyTarget(String(nextSettings.dailyCalorieTarget));
      setDefaultWeight(formatWeight(nextSettings.defaultWeight));
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

  const saveDefaultWeight = async () => {
    const parsedWeight = Number(defaultWeight);
    if (!Number.isFinite(parsedWeight) || parsedWeight < 0) {
      setStatusMessage('Default weight must be zero or more.');
      return;
    }

    setIsSavingDefaultWeight(true);
    setStatusMessage(null);

    try {
      const nextSettings = await sculptSettings.setDefaultWeight(parsedWeight);
      setSettings(nextSettings);
      setDefaultWeight(formatWeight(nextSettings.defaultWeight));
      setStatusMessage('Default weight updated.');
    } catch {
      setStatusMessage('Could not update default weight.');
    } finally {
      setIsSavingDefaultWeight(false);
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

  const saveStepPollingMinutes = async (seconds: number) => {
    setIsSavingStepPolling(true);
    setStatusMessage(null);

    try {
      const nextSettings = await sculptSettings.setStepPollingMinutes(seconds);
      setSettings(nextSettings);
      setStatusMessage(`Step refresh set to every ${formatPollingOption(seconds)}.`);
    } catch {
      setStatusMessage('Could not update step refresh interval.');
    } finally {
      setIsSavingStepPolling(false);
    }
  };

  const refreshSteps = async () => {
    setIsRefreshingSteps(true);
    setStatusMessage(null);

    try {
      const nextSettings = await sculptSettings.refreshSteps();
      setSettings(nextSettings);
      setStatusMessage('Steps refreshed.');
    } catch {
      setStatusMessage('Could not refresh steps.');
    } finally {
      setIsRefreshingSteps(false);
    }
  };

  const enableStepsPermission = async () => {
    if (Platform.OS !== 'android') {
      return;
    }

    if (Platform.Version < 29) {
      await refreshSteps();
      return;
    }

    try {
      const result = await PermissionsAndroid.request(
        PermissionsAndroid.PERMISSIONS.ACTIVITY_RECOGNITION,
      );
      if (result === PermissionsAndroid.RESULTS.GRANTED) {
        setStatusMessage('Steps permission granted.');
        await refreshSteps();
        return;
      }
      setStatusMessage('Steps permission was denied.');
    } catch {
      setStatusMessage('Could not request steps permission.');
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
      setDefaultWeight(formatWeight(nextSettings.defaultWeight));
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
  const progress =
    settings && settings.dailyCalorieTarget > 0
      ? Math.max(0, Math.min(1, settings.caloriesConsumedToday / settings.dailyCalorieTarget))
      : 0;
  const accentColor = accentFromProgress(progress);
  const accentSoft = withAlpha(accentColor, 0.18);
  const refreshCountdown = formatCountdown((settings?.nextStepRefreshAtMillis ?? 0) - nowMillis);

  return (
    <SafeAreaView style={styles.screen}>
      <StatusBar barStyle="light-content" backgroundColor="#09111e" />
      <ScrollView contentContainerStyle={styles.content}>
        <View style={styles.hero}>
          <Text style={[styles.eyebrow, {color: accentColor}]}>Sculpt control panel</Text>
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
              {backgroundColor: accentColor},
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
              {backgroundColor: accentColor},
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
          <Text style={styles.cardTitle}>Default weight</Text>
          <Text style={styles.cardBody}>
            This seeds widget weight tracking before any meal has been logged. The current saved
            value is <Text style={styles.inlineHighlight}>{formatWeight(settings?.defaultWeight ?? 70)} kg</Text>.
          </Text>
          <TextInput
            keyboardType="decimal-pad"
            onChangeText={setDefaultWeight}
            placeholder="70.0"
            placeholderTextColor="#7b8a9c"
            style={styles.input}
            value={defaultWeight}
          />
          <Pressable
            disabled={isSavingDefaultWeight}
            onPress={saveDefaultWeight}
            style={({pressed}) => [
              styles.primaryButton,
              {backgroundColor: accentColor},
              (pressed || isSavingDefaultWeight) && styles.buttonPressed,
            ]}>
            {isSavingDefaultWeight ? (
              <ActivityIndicator color="#ffffff" />
            ) : (
              <Text style={styles.primaryButtonText}>Save default weight</Text>
            )}
          </Pressable>
        </View>

        <View style={styles.card}>
          <Text style={styles.cardTitle}>Steps tracking</Text>
          <Text style={styles.cardBody}>
            The widget refreshes steps in the top middle panel. Current saved steps:{' '}
            <Text style={styles.inlineHighlight}>{settings?.todaySteps ?? 0}</Text>.
          </Text>
          <Text style={styles.cardBody}>
            Sensor:{' '}
            <Text style={styles.inlineHighlight}>
              {settings?.stepSensorAvailable ? 'available' : 'missing'}
            </Text>
            {'  '}Permission:{' '}
            <Text style={styles.inlineHighlight}>
              {settings?.stepsPermissionGranted ? 'granted' : 'required'}
            </Text>
          </Text>
          <Text style={styles.cardBody}>
            Status:{' '}
            <Text style={styles.inlineHighlight}>{formatStepStatus(settings?.stepStatus)}</Text>
          </Text>
          <Text style={styles.cardBody}>
            Next automatic refresh in{' '}
            <Text style={styles.inlineHighlight}>{refreshCountdown}</Text>.
          </Text>
          <View style={styles.optionRow}>
            {STEP_POLLING_OPTIONS.map(seconds => {
              const isSelected = settings?.stepPollingSeconds === seconds;
              return (
                <Pressable
                  key={seconds}
                  disabled={isSavingStepPolling}
                  onPress={() => saveStepPollingMinutes(seconds)}
                  style={({pressed}) => [
                    styles.optionChip,
                    isSelected && {borderColor: accentColor, backgroundColor: accentSoft},
                    (pressed || isSavingStepPolling) && styles.buttonPressed,
                  ]}>
                  <Text style={[styles.optionChipText, isSelected && {color: '#f8fafc'}]}>
                    {formatPollingOption(seconds)}
                  </Text>
                </Pressable>
              );
            })}
          </View>
          {!settings?.stepsPermissionGranted ? (
            <Pressable
              onPress={enableStepsPermission}
              style={({pressed}) => [
                styles.primaryButton,
                {backgroundColor: accentColor, marginTop: 6},
                pressed && styles.buttonPressed,
              ]}>
              <Text style={styles.primaryButtonText}>Enable steps permission</Text>
            </Pressable>
          ) : null}
          <Pressable
            disabled={
              isRefreshingSteps || !settings?.stepSensorAvailable || !settings?.stepsPermissionGranted
            }
            onPress={refreshSteps}
            style={({pressed}) => [
              styles.secondaryButton,
              (pressed ||
                isRefreshingSteps ||
                !settings?.stepSensorAvailable ||
                !settings?.stepsPermissionGranted) && styles.buttonPressed,
            ]}>
            {isRefreshingSteps ? (
              <ActivityIndicator color="#d6dfeb" />
            ) : (
              <Text style={styles.secondaryButtonText}>Refresh steps now</Text>
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
          {statusMessage ? (
            <Text style={[styles.statusMessage, {color: accentColor}]}>{statusMessage}</Text>
          ) : null}
        </View>

        <View style={[styles.card, {borderColor: accentSoft}]}>
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
  optionRow: {
    flexDirection: 'row',
    flexWrap: 'wrap',
    marginBottom: 6,
  },
  optionChip: {
    backgroundColor: '#0a1526',
    borderColor: '#304662',
    borderRadius: 999,
    borderWidth: 1,
    marginBottom: 10,
    marginRight: 10,
    paddingHorizontal: 14,
    paddingVertical: 10,
  },
  optionChipText: {
    color: '#c1ccda',
    fontSize: 14,
    fontWeight: '700',
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
    fontSize: 14,
    lineHeight: 20,
    marginTop: 4,
  },
});

function accentFromProgress(progress: number): string {
  const clamped = Math.max(0, Math.min(1, progress));
  const hue = 135 - 135 * clamped;
  return hsvToHex(hue, 0.72, 0.88);
}

function withAlpha(hexColor: string, alpha: number): string {
  const normalized = Math.max(0, Math.min(1, alpha));
  const alphaHex = Math.round(normalized * 255)
    .toString(16)
    .padStart(2, '0');
  return `${hexColor}${alphaHex}`;
}

function formatWeight(weight: number): string {
  return weight.toFixed(1);
}

const STEP_POLLING_OPTIONS = [30, 15 * 60, 30 * 60, 60 * 60, 120 * 60];

function formatPollingOption(seconds: number): string {
  if (seconds < 60) {
    return `${seconds}s`;
  }

  const minutes = seconds / 60;
  return `${minutes}m`;
}

function formatStepStatus(status?: string | null): string {
  switch (status) {
    case 'READY':
      return 'Ready';
    case 'BASELINE_PENDING':
      return 'Waiting for baseline';
    case 'STALE_READING':
      return 'Using last reading';
    case 'READ_FAILED':
      return 'Read failed';
    case 'PERMISSION_REQUIRED':
      return 'Permission required';
    case 'SENSOR_UNAVAILABLE':
      return 'Sensor unavailable';
    default:
      return 'Unknown';
  }
}

function formatCountdown(milliseconds: number): string {
  if (milliseconds <= 0) {
    return 'due now';
  }

  const totalSeconds = Math.ceil(milliseconds / 1000);
  const minutes = Math.floor(totalSeconds / 60);
  const seconds = totalSeconds % 60;
  if (minutes <= 0) {
    return `${seconds}s`;
  }
  return `${minutes}m ${String(seconds).padStart(2, '0')}s`;
}

function hsvToHex(h: number, s: number, v: number): string {
  const c = v * s;
  const x = c * (1 - Math.abs(((h / 60) % 2) - 1));
  const m = v - c;

  let r = 0;
  let g = 0;
  let b = 0;

  if (h < 60) {
    r = c;
    g = x;
  } else if (h < 120) {
    r = x;
    g = c;
  } else if (h < 180) {
    g = c;
    b = x;
  } else if (h < 240) {
    g = x;
    b = c;
  } else if (h < 300) {
    r = x;
    b = c;
  } else {
    r = c;
    b = x;
  }

  const toHex = (value: number) =>
    Math.round((value + m) * 255)
      .toString(16)
      .padStart(2, '0');

  return `#${toHex(r)}${toHex(g)}${toHex(b)}`;
}

export default App;
