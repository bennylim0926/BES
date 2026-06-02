import { Client } from "@stomp/stompjs"

// const WS_URL = "ws://localhost/ws"
const WS_URL = `${window.location.origin.replace(/^http/, "ws")}/ws`;

export const createClient = () =>{
    return new Client({
        brokerURL: WS_URL,
        reconnectDelay: 5000
    })
}
export const subscribeToChannel = (client, topic, callback) =>{
    const doSubscribe = () => {
        client.subscribe(topic, (msg) => {
            callback(JSON.parse(msg.body))
        })
    }
    if(!client.connected){
        const prev = client.onConnect
        client.onConnect = () => {
            if (prev) prev()
            doSubscribe()
        }
    }else{
        doSubscribe()
    }
    if (!client.active) client.activate()
}

export const deactivateClient = (client) =>{
    if(client)
        client.deactivate()
}