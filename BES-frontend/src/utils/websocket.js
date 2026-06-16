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
    let storedSub = null
    const doSubscribe = () => {
        storedSub = client.subscribe(topic, (msg) => {
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
    return { unsubscribe: () => { if (storedSub) storedSub.unsubscribe() } }
}

export const deactivateClient = (client) =>{
    if(client)
        client.deactivate()
}