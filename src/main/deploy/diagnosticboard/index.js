import {NT4_Client} from "./NT4.js";

const canIDMap = new Map();
const stateMap = new Map();
const controlMap = new Map();
const valueMap = new Map();

const defaultNt4Port = 5810;

const queryParams = new URLSearchParams(window.location.search);
const {host: ntHost, port: ntPort} = resolveNtConnectionParams(queryParams);

let ntConnected = false;

const ntClient = new NT4_Client(
    ntHost,
    "OperatorBoard",
    () => {
    },
    () => {
    },
    (topic, _, value) => handleTopicUpdate(topic, value),
    () => {
        ntConnected = true;
    },
    () => {
        ntConnected = false;
    },
    {port: ntPort}
);

ntClient.subscribe(["/MotorData/"], true, false, 0.05);
ntClient.connect();

const MOTOR_TABLE_PREFIX = "/MotorData/";
const CAN_KEY = "CAN/"
const STATES_KEY = "States/"
const CONTROL_KEY = "ControlMode/"
const VALUE_KEY = "Value/"

function handleTopicUpdate(topic, value) {
    if (topic.name.startsWith(`${MOTOR_TABLE_PREFIX}${CAN_KEY}`)) {
        const key = topic.name.slice(MOTOR_TABLE_PREFIX.length + CAN_KEY.length);
        canIDMap.set(key, value);
    }
    if (topic.name.startsWith(`${MOTOR_TABLE_PREFIX}${STATES_KEY}`)) {
        const key = topic.name.slice(MOTOR_TABLE_PREFIX.length + STATES_KEY.length);
        stateMap.set(key, value);
    }
    if (topic.name.startsWith(`${MOTOR_TABLE_PREFIX}${CONTROL_KEY}`)) {
        const key = topic.name.slice(MOTOR_TABLE_PREFIX.length + CONTROL_KEY.length);
        controlMap.set(key, value);
    }
    if (topic.name.startsWith(`${MOTOR_TABLE_PREFIX}${VALUE_KEY}`)) {
        const key = topic.name.slice(MOTOR_TABLE_PREFIX.length + VALUE_KEY.length);
        valueMap.set(key, value);
    }
    updateTable();
}

function buildRow(name, canID, control, input, isEnabled){
    return `
        <tr>
            <td>${name}</td>
            <td>${canID}</td>
            <td><input id = "ControlToggle${canID}" type="button" value="${control}" onclick="this.value=(this.value === 'Voltage' ? 'Throttle' : 'Voltage'); updateInputs('ControlToggle', '${name}', ${canID}, this.value);"></td>
            <td><input id = "Input${canID}" min = "${control === "Voltage" ? -12.0 : -1.0}" max = "${control === "Velocity" ? 12.0 : 1.0}" value = "0" onchange="updateInputs('ControlToggle', '${name}', ${canID}, this.value);" /></td>
            <td><input id = "State${canID}" type="button" value="${isEnabled ? 'Enabled' : 'Disabled'}" onclick="this.value=(this.value === 'Enabled' ? 'Disabled' : 'Enabled'); updateInputs('State', '${name}', ${canID}, this.value);"></td>
        </tr>
    `
}


function updateInputs(type, name, canID, value){
    switch(type){
        case "ControlToggle":
            controlMap.set(name, value);
            break;
        case "Input":
            valueMap.set(name, value);
            break;
        case "State":
            stateMap.set(name, value === "Enabled");
            break;
    }
}

function updateTable() {
    let rowsHtml = `
        <tr>
            <th>Name</th>
            <th>CAN ID</th>
            <th>Control Mode (Voltage/Throttle)</th>
            <th>Input</th>
            <th>Enabled/Disabled</th>
        </tr>
    `
    canIDMap.forEach((canID,name) => {
        const control = controlMap.get(name);
        const input = valueMap.get(name);
        const isEnabled = stateMap.get(name);

        rowsHtml += buildRow(name, canID, control, input, isEnabled);
    })

    document.getElementById("data-table").innerHTML=rowsHtml;
}

function resolveNtConnectionParams(params) {
    const host = params.get("ntHost") || params.get("host") || window.location.hostname || "localhost";
    const portRaw = params.get("ntPort") || params.get("port");
    const portParsed = portRaw ? Number(portRaw) : NaN;
    const port = Number.isFinite(portParsed) ? portParsed : defaultNt4Port;
    return {host, port};
}

window.updateInputs = updateInputs;