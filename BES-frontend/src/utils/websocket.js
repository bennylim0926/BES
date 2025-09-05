import { Client } from "@stomp/stompjs"

const WS_URL = "ws://localhost:5050/ws"

export const createClient = () =>{
    return new Client({
        brokerURL: WS_URL,
        reconnectDelay: 5000
    })
}
export const subscribeToChannel = (client, topic, callback) =>{
    if(!client.connected){
        client.onConnect = ()=>{
            client.subscribe(topic, (msg) =>{
                callback(JSON.parse(msg.body))
            })
        }
    }else{
        client.subscribe(topic, (msg)=>{
            callback(JSON.parse(msg.body))
        })
    }
    client.activate()
}

export const deactivateClient = (client) =>{
    if(client)
        client.deactivate()
}