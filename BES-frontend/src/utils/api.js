// const domain = "http://localhost:5050"
const domain = ""

export const login = async (username, password) =>{
  try{
    return await fetch(`${domain}/api/v1/auth/login`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
          username: username,
          password: password,
      })
    })
  }catch(err){
    console.log(err)
  }
}

export const logout = async () =>{
  try{
    return await fetch(`${domain}/api/v1/auth/logout`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      }
    })
  }catch(err){
    console.log(err)
  }
}

export const whoami = async () =>{
  try{
    const res =  await fetch(`${domain}/api/v1/auth/me`,{
      credentials: 'include'
    })
    return await res.json()

  }catch(err){
    console.log(err)
  }
}

export const fetchAllEvents = async () =>{
    try{
        const res = await fetch(`${domain}/api/v1/folders`,{
          method: 'GET',
          credentials: 'include'
        })
        return await res.json()
      }catch(err){
        console.log(err)
      }
}

export const fetchAllGenres = async () =>{
  try{
    const res = await fetch(`${domain}/api/v1/event/genre`,{
      method: 'GET',
      credentials: 'include'
    })
    return await res.json()
  }catch(e){
      console.log(e)
  }
}

export const checkTableExist = async (eventName, tableExist) =>{
  try{
    const res = await fetch(`${domain}/api/v1/event/${eventName.value}`,{
      credentials: 'include'
    })
    tableExist.value = await res.json();
  }catch(e){
      console.log(e)
  }
}

export const getFileId = async (folderId) =>{
  try{
    const res = await fetch(`${domain}/api/v1/files/${folderId}`,{
      credentials: 'include'
    })
    const result = await res.json()
    return result[0].fileId
  }catch(e){
      console.log(e)
  }
}

export const getResponseDetails = async(fileId) =>{
  try{
      const res = await fetch(`${domain}/api/v1/sheets/participants/breakdown/${fileId}`,{
        credentials: 'include'
      })
      if (!res.ok) throw new Error('Failed to read')
      return await res.json()
  }catch(err){
  }
}

export const getSheetSize = async(fileId) =>{
  try{
      const res = await fetch(`${domain}/api/v1/sheets/participants/size/${fileId}`,{
        credentials: 'include'
      })
      if (!res.ok) throw new Error('Failed to read')
      return await res.json()
  }catch(err){
  }
}

// 
export const getVerifiedParticipantsByEvent = async(eventName) =>{
  try{
    const res = await fetch(`${domain}/api/v1/event/verified-participant/${eventName}`,{
      credentials: 'include'
    })
    if(res.ok){
        return await res.json()
    }else if (res.status === 404) {
        return []
    }
  }catch(e){
      console.log(e)
  }
}

export const getRegisteredParticipantsByEvent = async(eventName) =>{
  try{
    const res = await fetch(`${domain}/api/v1/event/participants/${eventName}`,{
      credentials: 'include',
    })
    if(res.ok){
        return await res.json()
    }else if (res.status === 404) {
        return []
    }
  }catch(e){
      console.log(e)
  }
}

export const getAllJudges = async() =>{
  try{
    const res = await fetch(`${domain}/api/v1/event/judges`,{
      credentials: 'include'
    })
    return await res.json()
  }catch(err){
    console.log(err)
  }
}

export const addJudges = async(judgeList) => {
  try{
    await fetch(`${domain}/api/v1/event/judges`, {
      method: 'POST',
      credentials: 'include',
      headers: {
          'Accept': 'application/json',
          'Content-Type': 'application/json'
      },
      body: JSON.stringify({
          judges: judgeList
      })
  })
  }catch(e){
      console.log(e)
  }
}

export const insertPaymenColumnInSheet = async (fileId) =>{
  try{
    await fetch(`${domain}/api/v1/sheets/payment-status`, {
      method: 'POST',
      credentials: 'include',
      headers: {
          'Accept': 'application/json',
          'Content-Type': 'application/json'
      },
      body: JSON.stringify({
          fileId: fileId,
      })
  })
  }catch(e){
      console.log(e)
  }
}

export const insertEventInTable = async (eventName) =>{
  try{
    await fetch(`${domain}/api/v1/event`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
          eventName: eventName,
      })
  })
  }catch(e){
      console.log(e)
  }
}

export const linkGenreToEvent = async(eventName, genres) =>{
  try{
    return await fetch(`${domain}/api/v1/event/genre`, {
      method: 'POST',
      credentials: 'include',
      headers: {
      'Accept': 'application/json',
      'Content-Type': 'application/json'
      },
      body: JSON.stringify({
          eventName: eventName,
          genreName: genres
      })
  })
  }catch(e){
      console.log(e)
  }
}

export const addParticipantToSystem = async (fileId, eventName)=>{
  try{
  return await fetch(`${domain}/api/v1/event/participants/`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Accept': 'application/json',
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
        fileId: fileId,
        eventName: eventName,
    })
  })
  }catch(e){
    console.log(e)
  }
}

export const addWalkinToSystem = async (participantName, eventName, genreName, judgeName)=>{
  try{
  return await fetch(`${domain}/api/v1/event/walkins/`, {
    method: 'POST',
    credentials: 'include',
    headers: {
      'Accept': 'application/json',
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
        name: participantName,
        eventName: eventName,
        genre: genreName,
        judgeName: judgeName
    })
  })
  }catch(e){
    console.log(e)
  }
}

export const submitParticipantScore = async (eventName, genreName, judgeName, participants) =>{
  try{
    return await fetch(`${domain}/api/v1/event/scores`,{
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        eventName: eventName,
        genreName: genreName,
        judgeName : judgeName,
        participantScore: participants
      })
    })
  }catch(e){
    console.log(e)
  }
}

export const getParticipantScore = async(eventName) =>{
    try{
      const res = await fetch(`${domain}/api/v1/event/scores/${eventName}`,{
        credentials: 'include'
      })
      if(res.ok){
          return await res.json()
      }else if (res.status === 404) {
          return []
      }
    }catch(e){
        console.log(e)
    }
}

export const getBattleJudges = async() =>{
  try{
    const res = await fetch(`${domain}/api/v1/battle/judges`,{
      credentials: 'include'
    })
    if(res.ok){
      return await res.json()
    }
  }catch(e){
    console.log(e)
  }
}

export const getCurrentBattlePair = async()=>{
  try{
    const res = await fetch(`${domain}/api/v1/battle/battle-pair`,{
      credentials: 'include'
    })
    if(res.ok){
      return await res.json()
    }
  }catch(e){

  }
}