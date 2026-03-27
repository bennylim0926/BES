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

export const fetchAllFolderEvents = async () =>{
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

export const fetchAllEvents = async () =>{
  try{
      const res = await fetch(`${domain}/api/v1/event/events`,{
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
    if(result.length === 0){
      return null
    }
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

export const insertEventInTable = async (eventName, paymentRequired = false) =>{
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
          paymentRequired: paymentRequired,
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
      }
      return []
    }catch(e){
        console.log(e)
        return []
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

export const battleJudgeVote = async(id, vote) =>{
  try{
    return await fetch(`${domain}/api/v1/battle/vote`,{
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        id: Number(id),
        vote: Number(vote)
      })
    })
  }catch(e){

  }
}

export const addBattleJudge = async(id) =>{
  try{
    return await fetch(`${domain}/api/v1/battle/judge`,{
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        id: Number(id),
      })
    })
  }catch(e){

  }
}

export const removeBattleJudge = async(id) =>{
  try{
    return await fetch(`${domain}/api/v1/battle/judge`,{
      method: 'DELETE',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        id: Number(id),
      })
    })
  }catch(e){

  }
}
export const setBattlePair = async(leftBattler, rightBattler) =>{
  try{
    return await fetch(`${domain}/api/v1/battle/battle-pair`,{
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        leftBattler: leftBattler,
        rightBattler: rightBattler
      })
    })
  }catch(e){

  }
}

export const setBattleScore = async() =>{
  try{
    return await fetch(`${domain}/api/v1/battle/score`,{
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      }
    })
  }catch(e){

  }
}

export const uploadImage = async(file)=>{
  try{
    const formData = new FormData();
    formData.append("file", file);
    return await fetch(`${domain}/api/v1/battle/upload`,{
      method: 'POST',
      credentials: 'include',
      body: formData
    })
  }catch(e){

  }
}

export const getImage = async (filename) => {
  try {
    const res = await fetch(`${domain}/api/v1/battle/uploads/${filename}`, {
      method: 'GET',
      credentials: 'include',
    });

    if (!res.ok) {
      throw new Error(`Failed to fetch image: ${res.status}`);
    }

    // Convert the image stream into a blob
    const blob = await res.blob();

    // Create a temporary local URL for use in <img>
    const imageUrl = URL.createObjectURL(blob);

    return imageUrl;
  } catch (err) {
    console.error('Error fetching image:', err);
    return null;
  }
};

export const getSmokeList = async()=>{
  try{
    const res = await fetch(`${domain}/api/v1/battle/smoke`,{
      credentials: 'include'
    })
    if(res.ok){
        return await res.json()
    }
  }catch(e){
      console.log(e)
  }
}
export const getEmailTemplate = async (eventName) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/email-template`, {
      credentials: 'include'
    })
    if (!res.ok) return null
    return await res.json()
  } catch (e) {
    console.log(e)
  }
}

export const updateEmailTemplate = async (eventName, subject, body) => {
  try {
    return await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/email-template`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ subject, body })
    })
  } catch (e) {
    console.log(e)
  }
}

export const getUnverifiedParticipants = async (fileId) => {
  try {
    const res = await fetch(`${domain}/api/v1/sheets/participants/unverified/${fileId}`, {
      credentials: 'include'
    })
    if (res.ok) return await res.json()
    return []
  } catch (e) {
    console.log(e)
    return []
  }
}

export const getUnverifiedParticipantsDB = async (eventName) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/unverified-participants`, {
      credentials: 'include'
    })
    if (res.ok) return await res.json()
    return []
  } catch (e) {
    console.log(e)
    return []
  }
}

export const verifyAndEmailParticipant = async (participantId, eventId) => {
  try {
    return await fetch(`${domain}/api/v1/event/participants/verify-email`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ participantId, eventId })
    })
  } catch (e) {
    console.log(e)
  }
}

export const verifyAndEmailBatch = async (list) => {
  try {
    return await fetch(`${domain}/api/v1/event/participants/verify-email-batch`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify(list)
    })
  } catch (e) {
    console.log(e)
  }
}

export const getGenresByEvent = async (eventName) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/genres`, {
      credentials: 'include'
    })
    if (res.ok) return await res.json()
    return []
  } catch (e) {
    console.log(e)
    return []
  }
}

export const removeParticipantGenre = async (participantId, eventId, genreId) => {
  try {
    return await fetch(`${domain}/api/v1/event/participant-genre/${participantId}/${eventId}/${genreId}`, {
      method: 'DELETE',
      credentials: 'include'
    })
  } catch (e) {
    console.log(e)
  }
}

export const addGenreToParticipant = async (participantId, eventId, genreName) => {
  try {
    return await fetch(`${domain}/api/v1/event/participant-genre`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ participantId, eventId, genreName })
    })
  } catch (e) {
    console.log(e)
  }
}

export const updateSmokeList  = async(battlers)=>{
  try{
    return await fetch(`${domain}/api/v1/battle/smoke`,{
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        battlers: battlers
      })
    })
  }catch(e){

  }
}

export const verifyEventAccessCode = async (eventId, accessCode) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/verify-access-code`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ eventId, accessCode })
    })
    return await res.json()
  } catch (err) {
    console.log(err)
  }
}

export const updateEventAccessCode = async (eventId, newCode) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/access-code`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ eventId, newCode })
    })
    return await res.json()
  } catch (err) {
    console.log(err)
  }
}

export const getJudgingMode = async (eventName) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/judging-mode/${encodeURIComponent(eventName)}`, {
      credentials: 'include'
    })
    if (res.ok) return await res.json()
    return null
  } catch (e) {
    console.log(e)
    return null
  }
}

export const setJudgingMode = async (eventName, mode) => {
  try {
    return await fetch(`${domain}/api/v1/event/judging-mode`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ eventName, judgingMode: mode })
    })
  } catch (e) {
    console.log(e)
  }
}

export const submitAuditionFeedback = async (eventName, genreName, judgeName, auditionNumber, tagIds, note) => {
  try {
    return await fetch(`${domain}/api/v1/event/feedback`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ eventName, genreName, judgeName, auditionNumber, tagIds, note })
    })
  } catch (e) {
    console.log(e)
  }
}

export const getAuditionFeedback = async (eventName, genreName, judgeName, auditionNumber) => {
  try {
    const params = new URLSearchParams({ eventName, genreName, judgeName, auditionNumber })
    const res = await fetch(`${domain}/api/v1/event/feedback?${params}`, { credentials: 'include' })
    if (res.ok) return await res.json()
    return null
  } catch (e) {
    console.log(e)
    return null
  }
}

export const getParticipantFeedback = async (eventName, genreName, participantName) => {
  try {
    const params = new URLSearchParams({ eventName, genreName, participantName })
    const res = await fetch(`${domain}/api/v1/event/feedback/participant?${params}`, { credentials: 'include' })
    if (res.ok) return await res.json()
    return []
  } catch (e) {
    console.log(e)
    return []
  }
}

export const getResultsStatus = async (eventName) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/results-status`, { credentials: 'include' })
    if (res.ok) return await res.json()
    return null
  } catch (e) {
    console.log(e)
    return null
  }
}

export const releaseResults = async (eventName, released) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/release-results`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ released })
    })
    if (res.ok) return await res.json()
    return null
  } catch (e) {
    console.log(e)
    return null
  }
}

export const getParticipantRefs = async (eventName) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/participant-refs`, { credentials: 'include' })
    if (res.ok) return await res.json()
    return []
  } catch (e) {
    console.log(e)
    return []
  }
}

export const getResultsByRefCode = async (refCode) => {
  try {
    const res = await fetch(`${domain}/api/v1/results?ref=${encodeURIComponent(refCode)}`)
    if (res.ok) return await res.json()
    const body = await res.json().catch(() => ({}))
    return { error: body.error || 'Results not found or not yet released' }
  } catch (e) {
    console.log(e)
    return { error: 'Network error' }
  }
}