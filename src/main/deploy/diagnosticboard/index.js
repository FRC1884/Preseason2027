import {NT4_Client} from "./NT4";

const canIDMap = new Map();
const stateMap = new Map();
const controlMap = new Map();
const valueMap = new Map();

const queryParams = new URLSearchParams(window.location.search);
const { host: ntHost, port: ntPort } = resolveNtConnectionParams(queryParams);

let ntConnected = false;

const ntClient = new NT4_Client(
    ntHost,
    "OperatorBoard",
    () => {},
    () => {},
    (topic, _, value) => handleTopicUpdate(topic, value),
    () => {
        ntConnected = true;
        updateChips();
    },
    () => {
        ntConnected = false;
        updateChips();
    },
    { port: ntPort }
);

const MOTOR_TABLE_PREFIX = "/MotorData/";
const CAN_KEY = "CAN/"
const STATES_KEY = "States/"
const CONTROL_KEY = "ControlMode/"
const VALUE_KEY = "Value/"

function handleTopicUpdate(topic, value){
    if (topic.name.startsWith(`${MOTOR_TABLE_PREFIX}${CAN_KEY}`)){
        const key = topic.name.slice(MOTOR_TABLE_PREFIX.length+CAN_KEY.length);

        canIDMap.set(key, value);
    }
    if (topic.name.startsWith(`${MOTOR_TABLE_PREFIX}${STATES_KEY}`)){
        const key = topic.name.slice(MOTOR_TABLE_PREFIX.length+STATES_KEY.length);

        stateMap.set(key, value);
    }
    if (topic.name.startsWith(`${MOTOR_TABLE_PREFIX}${CONTROL_KEY}`)){
        const key = topic.name.slice(MOTOR_TABLE_PREFIX.length+CONTROL_KEY.length);

        controlMap.set(key, value);
    }
    if (topic.name.startsWith(`${MOTOR_TABLE_PREFIX}${VALUE_KEY}`)){
        const key = topic.name.slice(MOTOR_TABLE_PREFIX.length+VALUE_KEY.length);

        valueMap.set(key, value);
    }
}

function resolveNtConnectionParams(params) {
    const host = params.get("ntHost") || params.get("host") || window.location.hostname || "localhost";
    const portRaw = params.get("ntPort") || params.get("port");
    const portParsed = portRaw ? Number(portRaw) : NaN;
    const port = Number.isFinite(portParsed) ? portParsed : defaultNt4Port;
    return { host, port };
}

function updateChips() {
    const now = Date.now();
    const stale = now - lastAnyData > STALE_MS;

    setChip(ui.chipNt, ntConnected ? "NT OK" : "NT OFF", ntConnected ? "ok" : "bad");
    setChip(ui.chipData, stale ? "DATA STALE" : "DATA OK", stale ? "warn" : "ok");
    setChip(ui.chipMode, state.dsMode ? state.dsMode : "MODE", "none");
}