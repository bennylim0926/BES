// const domain = "http://localhost:5050"
const domain = ""
export const fetchAllEvents = async () =>{
    try{
        const res = await fetch(`${domain}/api/v1/folders`)
        return await res.json()
      }catch(err){
        console.log(err)
      }
}

export const fetchAllGenres = async () =>{
  try{
    const res = await fetch(`${domain}/api/v1/event/genre`)
    return await res.json()
  }catch(e){
      console.log(e)
  }
}

export const checkTableExist = async (eventName, tableExist) =>{
  try{
    const res = await fetch(`${domain}/api/v1/event/${eventName.value}`)
    tableExist.value = await res.json();
  }catch(e){
      console.log(e)
  }
}

export const getFileId = async (folderId) =>{
  try{
    const res = await fetch(`${domain}/api/v1/files/${folderId}`)
    const result = await res.json()
    return result[0].fileId
  }catch(e){
      console.log(e)
  }
}

export const getResponseDetails = async(fileId) =>{
  try{
      const res = await fetch(`${domain}/api/v1/sheets/participants/breakdown/${fileId}`)
      if (!res.ok) throw new Error('Failed to read')
      return await res.json()
  }catch(err){
  }
}

// 
export const getVerifiedParticipantsByEvent = async(eventName) =>{
  try{
    const res = await fetch(`${domain}/api/v1/event/verified-participant/${eventName}`)
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
    const res = await fetch(`${domain}/api/v1/event/participants/${eventName}`)
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
    const res = await fetch(`${domain}/api/v1/event/judges`)
    return await res.json()
  }catch(err){
    console.log(err)
  }
}

export const addJudges = async(judgeList) => {
  try{
    await fetch(`${domain}/api/v1/event/judges`, {
      method: 'POST',
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
      const res = await fetch(`${domain}/api/v1/event/scores/${eventName}`)
      if(res.ok){
          return await res.json()
      }else if (res.status === 404) {
          return []
      }
    }catch(e){
        console.log(e)
    }
}