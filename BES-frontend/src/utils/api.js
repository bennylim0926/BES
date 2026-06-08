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
    return { authenticated: false }
  }
}

export const fetchAllFolderEvents = async () =>{
    try{
        const res = await fetch(`${domain}/api/v1/folders`,{
          method: 'GET',
          credentials: 'include'
        })
        return res.ok ? await res.json() : []
      }catch(err){
        console.log(err)
        return []
      }
}

export const fetchAllEvents = async () =>{
  try{
      const res = await fetch(`${domain}/api/v1/event/events`,{
        method: 'GET',
        credentials: 'include'
      })
      return res.ok ? await res.json() : []
    }catch(err){
      console.log(err)
      return []
    }
}

export const fetchAllGenres = async () =>{
  try{
    const res = await fetch(`${domain}/api/v1/event/genre`,{
      method: 'GET',
      credentials: 'include'
    })
    return res.ok ? await res.json() : []
  }catch(e){
      console.log(e)
      return []
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
    console.log(err)
    return null
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
    console.log(err)
    return null
  }
}

// 
export const getVerifiedParticipantsByEvent = async(eventName) =>{
  try{
    const res = await fetch(`${domain}/api/v1/event/verified-participant/${eventName}`,{
      credentials: 'include'
    })
    return res.ok ? await res.json() : []
  }catch(e){
      console.log(e)
      return []
  }
}

export const getRegisteredParticipantsByEvent = async(eventName) =>{
  try{
    const res = await fetch(`${domain}/api/v1/event/participants/${eventName}`,{
      credentials: 'include',
    })
    return res.ok ? await res.json() : []
  }catch(e){
      console.log(e)
      return []
  }
}

export const getAllJudges = async() =>{
  try{
    const res = await fetch(`${domain}/api/v1/event/judges`,{
      credentials: 'include'
    })
    return res.ok ? await res.json() : []
  }catch(err){
    console.log(err)
    return []
  }
}

export const getEventJudges = async(eventName) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/judges`, { credentials: 'include' })
    return res.ok ? await res.json() : []
  } catch(err) { console.log(err); return [] }
}

export const addEventJudge = async(eventName, judgeName) => {
  try {
    return await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/judge`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ judgeName })
    })
  } catch(err) { console.log(err) }
}

export const removeEventJudge = async(eventName, judgeId) => {
  try {
    return await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/judge/${judgeId}`, {
      method: 'DELETE',
      credentials: 'include'
    })
  } catch(err) { console.log(err) }
}

export const getJudgesByEvent = async (eventName) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/judges`, { credentials: 'include', headers: { 'Accept': 'application/json' } })
    return res.ok ? await res.json() : []
  } catch { return [] }
}

export const getJudgeDivisions = async (eventName, judgeId) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/judge/${judgeId}/divisions`, { credentials: 'include', headers: { 'Accept': 'application/json' } })
    return res.ok ? await res.json() : []
  } catch { return [] }
}

export const addJudgeToEvent = async (eventName, judgeName) => {
  try {
    return await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/judge`, {
      method: 'POST', credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ judgeName })
    })
  } catch { return null }
}

export const assignJudgeToDivision = async (eventName, divisionId, judgeId) => {
  try {
    return await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/divisions/${divisionId}/assign-judge/${judgeId}`, {
      method: 'POST', credentials: 'include',
      headers: { 'Accept': 'application/json' }
    })
  } catch { return null }
}

export const getJudgesByDivision = async (eventName, divisionId) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/divisions/${divisionId}/judges`, { credentials: 'include', headers: { 'Accept': 'application/json' } })
    return res.ok ? await res.json() : []
  } catch { return [] }
}

export const addJudgeToDivision = async (eventName, divisionId, judgeName) => {
  try {
    return await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/divisions/${divisionId}/judge`, {
      method: 'POST', credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ judgeName })
    })
  } catch { return null }
}

export const removeJudgeFromDivision = async (eventName, divisionId, judgeId) => {
  try {
    return await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/divisions/${divisionId}/judge/${judgeId}`, {
      method: 'DELETE', credentials: 'include', headers: { 'Accept': 'application/json' }
    })
  } catch { return null }
}

export const insertPaymentColumnInSheet = async (fileId) =>{
  try{
    const res = await fetch(`${domain}/api/v1/sheets/payment-status`, {
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
    return res.ok
  }catch(e){
      console.log(e)
      return false
  }
}

export const insertEventInTable = async (eventName, paymentRequired = false) =>{
  try{
    const res = await fetch(`${domain}/api/v1/event`, {
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
    return res.ok
  }catch(e){
      console.log(e)
      return false
  }
}

export const linkGenreToEvent = async(eventName, divisions) =>{
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
          divisions: divisions
      })
  })
  }catch(e){
      console.log(e)
      return null
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

export const addWalkinToSystem = async (participantName, eventName, genreName, judgeName, teamMembers = [], teamName = '', entryMode = 'team')=>{
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
        judgeName: judgeName,
        teamMembers: teamMembers,
        teamName: teamName,
        entryMode: entryMode
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

export const resetJudgeScores = async (eventName, genreName, judgeName) => {
  const params = new URLSearchParams({ eventName, genreName, judgeName })
  try {
    return await fetch(`${domain}/api/v1/event/scores/reset?${params}`, {
      method: 'DELETE',
      credentials: 'include'
    })
  } catch (e) { console.log(e) }
}

export const resetJudgeFeedback = async (eventName, genreName, judgeName) => {
  const params = new URLSearchParams({ eventName, genreName, judgeName })
  try {
    return await fetch(`${domain}/api/v1/event/feedback/reset?${params}`, {
      method: 'DELETE',
      credentials: 'include'
    })
  } catch (e) { console.log(e) }
}

export const getBattleJudges = async(eventName = '') =>{
  try{
    const url = eventName
      ? `${domain}/api/v1/battle/judges?event=${encodeURIComponent(eventName)}`
      : `${domain}/api/v1/battle/judges`
    const res = await fetch(url,{
      credentials: 'include'
    })
    if(res.ok){
      return await res.json()
    }
  }catch(e){
    console.log(e)
  }
}

export const getCurrentBattlePair = async(eventName = '')=>{
  try{
    const url = eventName
      ? `${domain}/api/v1/battle/battle-pair?event=${encodeURIComponent(eventName)}`
      : `${domain}/api/v1/battle/battle-pair`
    const res = await fetch(url,{
      credentials: 'include'
    })
    return res.ok ? await res.json() : null
  }catch(_e){
    return null
  }
}

export const battleJudgeVote = async(id, vote, eventName = '') =>{
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
        vote: Number(vote),
        ...(eventName ? { eventName } : {})
      })
    })
  }catch(e){
    console.log(e)
    return null
  }
}

export const addBattleJudge = async(id, weightage = 1, eventName = '') =>{
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
        weightage: Math.max(1, Number(weightage) || 1),
        ...(eventName ? { eventName } : {})
      })
    })
  }catch(e){
    console.log(e)
    return null
  }
}

export const updateJudgeWeightage = async(id, weightage, eventName = '') =>{
  try{
    return await fetch(`${domain}/api/v1/battle/judge/weightage`,{
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        id: Number(id),
        weightage: Math.max(1, Number(weightage) || 1),
        ...(eventName ? { eventName } : {})
      })
    })
  }catch(e){
    console.log(e)
    return null
  }
}

export const removeBattleJudge = async(id, eventName = '') =>{
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
        ...(eventName ? { eventName } : {})
      })
    })
  }catch(e){
    console.log(e)
    return null
  }
}
export const setBattlePair = async(leftBattler, rightBattler, isFinal = false, leftMembers = [], rightMembers = [], eventName = '') =>{
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
        rightBattler: rightBattler,
        isFinal: isFinal,
        leftMembers: leftMembers,
        rightMembers: rightMembers,
        ...(eventName ? { eventName } : {})
      })
    })
  }catch(e){
    console.log(e)
    return null
  }
}

export const clearBattlePair = async (eventName = '') => {
  try {
    const url = eventName
      ? `${domain}/api/v1/battle/battle-pair?event=${encodeURIComponent(eventName)}`
      : `${domain}/api/v1/battle/battle-pair`
    return await fetch(url, {
      method: 'DELETE',
      credentials: 'include',
      headers: { 'Accept': 'application/json' }
    })
  } catch (e) {
    console.log(e)
    return null
  }
}

export const setBattleScore = async (isFinal = false, eventName = '') => {
  try {
    return await fetch(`${domain}/api/v1/battle/score`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ isFinal, ...(eventName ? { eventName } : {}) })
    })
  } catch (e) {
    console.log(e)
    return null
  }
}

export const resetBattleVotes = async (eventName = '') => {
  try {
    return await fetch(`${domain}/api/v1/battle/revote`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: eventName ? JSON.stringify({ eventName }) : undefined
    })
  } catch (e) {
    console.log(e)
    return null
  }
}

export const revealChampion = async (genreName, championName, eventName = '') => {
  try {
    return await fetch(`${domain}/api/v1/battle/champion-reveal`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ genreName, championName, dismiss: false, ...(eventName ? { eventName } : {}) })
    })
  } catch (e) {
    console.log(e)
    return null
  }
}

export const getBattleChampions = async (eventName) => {
  try {
    const res = await fetch(`${domain}/api/v1/battle/champions?event=${encodeURIComponent(eventName)}`, {
      credentials: 'include',
      headers: { 'Accept': 'application/json' }
    })
    return res.ok ? res.json() : {}
  } catch (e) {
    console.log(e)
    return {}
  }
}

export const dismissChampionReveal = async (eventName = '') => {
  try {
    return await fetch(`${domain}/api/v1/battle/champion-reveal`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ dismiss: true, ...(eventName ? { eventName } : {}) })
    })
  } catch (e) {
    console.log(e)
    return null
  }
}

export const setBracketState = async (rounds, topSize, currentRoundIndex = 0, eventName = '') => {
  try {
    return await fetch(`${domain}/api/v1/battle/bracket`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ rounds, topSize: String(topSize), currentRoundIndex, ...(eventName ? { eventName } : {}) })
    })
  } catch (e) { console.error(e) }
}

export const getBracketState = async (eventName = '') => {
  try {
    const url = eventName
      ? `${domain}/api/v1/battle/bracket?event=${encodeURIComponent(eventName)}`
      : `${domain}/api/v1/battle/bracket`
    const res = await fetch(url, { credentials: 'include' })
    return res.ok ? await res.json() : null
  } catch (_e) { return null }
}

export const setActiveGenre = async (eventName, genreName) => {
  try {
    return await fetch(`${domain}/api/v1/battle/active-genre`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ eventName, genreName })
    })
  } catch (e) { console.error(e) }
}


export const getBattleState = async (eventName = '') => {
  try {
    const url = eventName
      ? `${domain}/api/v1/battle/state?event=${encodeURIComponent(eventName)}`
      : `${domain}/api/v1/battle/state`
    const res = await fetch(url, { credentials: 'include' })
    return res.ok ? await res.json() : null
  } catch (_e) { return null }
}

export const getGenreStateFromDb = async (eventName, genreName) => {
  try {
    const res = await fetch(
      `${domain}/api/v1/battle/genre-state?event=${encodeURIComponent(eventName)}&genre=${encodeURIComponent(genreName)}`,
      { credentials: 'include' }
    )
    return res.ok ? await res.json() : null
  } catch (_e) { return null }
}

export const uploadBattleLogo = async (eventName, file) => {
  try {
    const formData = new FormData()
    formData.append('file', file)
    const url = eventName
      ? `/api/v1/battle/logo-upload?event=${encodeURIComponent(eventName)}`
      : '/api/v1/battle/logo-upload'
    return await fetch(`${domain}${url}`, {
      method: 'POST',
      credentials: 'include',
      body: formData,
    })
  } catch (e) {
    console.log(e)
    return null
  }
}

export const deleteBattleLogo = async (eventName) => {
  try {
    const url = eventName
      ? `/api/v1/battle/logo?event=${encodeURIComponent(eventName)}`
      : '/api/v1/battle/logo'
    return await fetch(`${domain}${url}`, {
      method: 'DELETE',
      credentials: 'include',
    })
  } catch (e) {
    console.log(e)
    return null
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
    console.log(e)
    return null
  }
}

export const getImage = async (filename) => {
  try {
    const res = await fetch(`${domain}/api/v1/battle/uploads/${encodeURIComponent(filename)}`, {
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

export const getBattlePhase = async (eventName = '') => {
  try {
    const url = eventName
      ? `${domain}/api/v1/battle/phase?event=${encodeURIComponent(eventName)}`
      : `${domain}/api/v1/battle/phase`
    const res = await fetch(url, { credentials: 'include' })
    return res.ok ? await res.json() : { phase: 'IDLE' }
  } catch (_e) { return { phase: 'IDLE' } }
}

export const setBattlePhase = async (phase, champion, eventName = '') => {
  try {
    return await fetch(`${domain}/api/v1/battle/phase`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ phase, ...(champion ? { champion } : {}), ...(eventName ? { eventName } : {}) })
    })
  } catch (e) { console.log(e) }
}

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

export const resetEmailTemplate = async (eventName) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/email-template/reset`, {
      method: 'POST',
      credentials: 'include'
    })
    if (res.ok) return await res.json()
    return null
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

export const verifyPayment = async (participantId, eventId) => {
  try {
    return await fetch(`${domain}/api/v1/event/participants/verify-payment`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ participantId, eventId })
    })
  } catch (e) {
    console.log(e)
  }
}

export const verifyPaymentBatch = async (list) => {
  try {
    return await fetch(`${domain}/api/v1/event/participants/verify-payment-batch`, {
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

export const addGenreToParticipant = async (participantId, eventId, genreName, entryMode, teamName, teamMembers) => {
  try {
    return await fetch(`${domain}/api/v1/event/participant-genre`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({ participantId, eventId, genreName, entryMode, teamName, teamMembers })
    })
  } catch (e) {
    console.log(e)
  }
}

export const updateSmokeList  = async(battlers, eventName = '')=>{
  try{
    return await fetch(`${domain}/api/v1/battle/smoke`,{
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json'
      },
      body: JSON.stringify({
        battlers: battlers,
        ...(eventName ? { eventName } : {})
      })
    })
  }catch(e){
    console.log(e)
    return null
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

export const getScoringCriteria = async (eventName, genreName) => {
  try {
    const params = genreName ? `?genre=${encodeURIComponent(genreName)}` : ''
    const res = await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/criteria${params}`, {
      credentials: 'include'
    })
    if (res.ok) return await res.json()
    return []
  } catch (e) {
    console.log(e)
    return []
  }
}

export const getScoringCriteriaStrict = async (eventName, genreName) => {
  try {
    const base = `${domain}/api/v1/event/${encodeURIComponent(eventName)}/criteria?strict=true`
    const url = genreName ? `${base}&genre=${encodeURIComponent(genreName)}` : base
    const res = await fetch(url, { credentials: 'include' })
    if (res.ok) return await res.json()
    return []
  } catch (e) {
    console.log(e)
    return []
  }
}

export const addScoringCriteria = async (eventName, payload) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/criteria`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
    if (res.ok) return await res.json()
    return null
  } catch (e) {
    console.log(e)
    return null
  }
}

export const updateScoringCriteria = async (eventName, criteriaId, payload) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/criteria/${criteriaId}`, {
      method: 'PUT',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify(payload)
    })
    if (res.ok) return await res.json()
    return null
  } catch (e) {
    console.log(e)
    return null
  }
}

export const deleteScoringCriteria = async (eventName, criteriaId) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/criteria/${criteriaId}`, {
      method: 'DELETE',
      credentials: 'include'
    })
    return res.ok
  } catch (e) {
    console.log(e)
    return false
  }
}

export const deleteAllCriteriaForGenre = async (eventName, genreName) => {
  try {
    const params = genreName ? `?genre=${encodeURIComponent(genreName)}` : ''
    const res = await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/criteria${params}`, {
      method: 'DELETE',
      credentials: 'include'
    })
    return res.ok
  } catch (e) {
    console.log(e)
    return false
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
export const getPickupCrews = async (eventName, genreName) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/crews/${encodeURIComponent(eventName)}/${encodeURIComponent(genreName)}`, {
      credentials: 'include'
    })
    if (res.ok) return await res.json()
    return []
  } catch (e) {
    console.log(e)
    return []
  }
}

export const createPickupCrew = async (eventName, genreName, crewName, memberParticipantIds) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/crews`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ eventName, genreName, crewName, memberParticipantIds })
    })
    if (res.ok) return await res.json()
    const body = await res.json().catch(() => ({}))
    return { error: body.error || 'Failed to create crew' }
  } catch (e) {
    console.log(e)
    return { error: 'Network error' }
  }
}

export const deletePickupCrew = async (crewId) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/crews/${crewId}`, {
      method: 'DELETE',
      credentials: 'include'
    })
    return res.ok
  } catch (e) {
    console.log(e)
    return false
  }
}

export const updateEventGenreFormat = async (eventName, eventGenreId, format) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/genres/${eventGenreId}/format`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ format })
    })
    return res.ok
  } catch (e) {
    console.log(e)
    return false
  }
}

export const getOverlayConfig = async (eventName = '') => {
  try {
    const url = eventName
      ? `${domain}/api/v1/battle/overlay-config?event=${encodeURIComponent(eventName)}`
      : `${domain}/api/v1/battle/overlay-config`
    const res = await fetch(url, {
      credentials: 'include',
    })
    return res.ok ? await res.json() : { showImages: true, leftColor: '#dc2626', rightColor: '#2563eb' }
  } catch (err) {
    console.log(err)
    return { showImages: true, leftColor: '#dc2626', rightColor: '#2563eb' }
  }
}

export const setOverlayConfig = async (config, eventName = '') => {
  try {
    return await fetch(`${domain}/api/v1/battle/overlay-config`, {
      method: 'POST',
      credentials: 'include',
      headers: {
        'Accept': 'application/json',
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ ...config, ...(eventName ? { eventName } : {}) }),
    })
  } catch (err) {
    console.log(err)
  }
}

export const getCheckinList = async (eventName) => {
  try {
    return await fetch(`${domain}/api/v1/event/${eventName}/checkin-list`, {
      credentials: 'include'
    })
  } catch (e) {
    console.log(e)
  }
}

export const checkInParticipant = async (participantId, eventId) => {
  try {
    return await fetch(`${domain}/api/v1/event/register-participant/${participantId}/${eventId}`, {
      credentials: 'include'
    })
  } catch (e) {
    console.log(e)
  }
}

export const sendCheckinPreview = async (eventName, previewData) => {
  try {
    return await fetch(`${domain}/api/v1/event/${eventName}/checkin-preview`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(previewData)
    })
  } catch (e) {
    console.log(e)
  }
}

export const getCheckinPreviews = async (eventName) => {
  try {
    const res = await fetch(`${domain}/api/v1/event/${eventName}/checkin-preview`, { credentials: 'include' })
    if (res.ok) return await res.json()
    return []
  } catch { return [] }
}

export const getBattleGuests = async (eventName) => {
  try {
    return await fetch(`${domain}/api/v1/event/battle-guests/${encodeURIComponent(eventName)}`, {
      credentials: 'include'
    })
  } catch (e) {
    console.log(e)
  }
}

export const addBattleGuest = async (eventName, genreName, guestName, entryRound, memberNames = []) => {
  try {
    return await fetch(`${domain}/api/v1/event/battle-guests/${encodeURIComponent(eventName)}/${encodeURIComponent(genreName)}`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ guestName, entryRound, memberNames })
    })
  } catch (e) {
    console.log(e)
  }
}

export const removeBattleGuest = async (guestId) => {
  try {
    return await fetch(`${domain}/api/v1/event/battle-guests/${guestId}`, {
      method: 'DELETE',
      credentials: 'include'
    })
  } catch (e) {
    console.log(e)
  }
}

export const getAppConfig = async () => {
  const res = await fetch('/api/v1/config/app', { credentials: 'include' })
  return res.json()
}

export const postAppConfig = async (accentColor) => {
  const res = await fetch('/api/v1/config/app', {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ accentColor })
  })
  return res.json()
}

export const addDivision = async (eventName, name, format, genreId) => {
  try {
    return await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/divisions`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ name, format: format || null, genreId: genreId || null })
    })
  } catch (e) { console.log(e) }
}

export const renameDivision = async (eventName, divisionId, name) => {
  try {
    return await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/divisions/${divisionId}/name`, {
      method: 'PATCH',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ name })
    })
  } catch (e) { console.log(e) }
}

export const updateDivisionAliases = async (eventName, divisionId, aliases) => {
  try {
    return await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/divisions/${divisionId}/aliases`, {
      method: 'PATCH',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ aliases })
    })
  } catch (e) { console.log(e) }
}

export const updateDivisionSoloAllowed = async (eventName, divisionId, soloAllowed) => {
  try {
    return await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/divisions/${divisionId}/solo-allowed`, {
      method: 'PATCH',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ soloAllowed })
    })
  } catch (e) { console.log(e) }
}

export const deleteDivision = async (eventName, divisionId) => {
  try {
    return await fetch(`${domain}/api/v1/event/${encodeURIComponent(eventName)}/divisions/${divisionId}`, {
      method: 'DELETE',
      credentials: 'include'
    })
  } catch (e) { console.log(e) }
}

export const getSheetCategories = async (fileId) => {
  try {
    const res = await fetch(`${domain}/api/v1/sheets/categories/${fileId}`, {
      credentials: 'include',
      headers: { 'Accept': 'application/json' }
    })
    return res.ok ? (await res.json()).values ?? [] : []
  } catch (e) { console.log(e); return [] }
}

export const assignAuditionNumber = async (eventId, participantId, eventGenreId, auditionNumber) => {
  try {
    return await fetch(`${domain}/api/v1/event/adjust/assign`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ eventId, participantId, eventGenreId, auditionNumber })
    })
  } catch (e) { console.log(e) }
}

export const assignAuditionNumbersBatch = async (eventId, participantId, assignments) => {
  // assignments: [{ eventGenreId, auditionNumber }, ...]
  try {
    return await fetch(`${domain}/api/v1/event/adjust/assign-batch`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ eventId, participantId, assignments })
    })
  } catch (e) { console.log(e) }
}

export const swapAuditionNumbers = async (eventId, eventGenreId, participantId1, participantId2) => {
  try {
    return await fetch(`${domain}/api/v1/event/adjust/swap`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ eventId, eventGenreId, participantId1, participantId2 })
    })
  } catch (e) { console.log(e) }
}

export const releaseAuditionNumbers = async (eventId, participantId) => {
  try {
    return await fetch(`${domain}/api/v1/event/adjust/release`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ eventId, participantId })
    })
  } catch (e) { console.log(e) }
}

export const redeemToken = async (tokenId) => {
  try {
    const res = await fetch(`${domain}/api/v1/auth/token`, {
      method: 'POST', credentials: 'include',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ tokenId })
    })
    if (!res.ok) {
      const body = await res.json().catch(() => null)
      return { authenticated: false, error: body?.error || 'Invalid or expired link.' }
    }
    return await res.json()
  } catch (err) {
    console.error(err)
    return { authenticated: false, error: 'Unable to reach server. Check your connection and try again.' }
  }
}

export const generateToken = async (role, eventId, judgeId, expiresInDays = 7) => {
  try {
    const params = new URLSearchParams({ role, eventId, expiresInDays })
    if (judgeId) params.append('judgeId', judgeId)
    const res = await fetch(`${domain}/api/v1/auth/generate-token?${params}`, {
      method: 'POST', credentials: 'include'
    })
    return res.ok ? await res.json() : null
  } catch (err) { console.error(err); return null }
}

export const getSessionTokens = async (eventId) => {
  try {
    const res = await fetch(`${domain}/api/v1/auth/tokens?eventId=${eventId}`, { credentials: 'include' })
    return res.ok ? await res.json() : []
  } catch (err) { console.error(err); return [] }
}

export const revokeSessionToken = async (tokenId) => {
  try {
    return await fetch(`${domain}/api/v1/auth/tokens/${tokenId}`, {
      method: 'DELETE', credentials: 'include'
    })
  } catch (err) { console.error(err) }
}

export const setResolvedParticipants = async (eventName, genreName, participants) => {
  try {
    return await fetch(`${domain}/api/v1/battle/resolved-participants`, {
      method: 'POST',
      credentials: 'include',
      headers: { 'Accept': 'application/json', 'Content-Type': 'application/json' },
      body: JSON.stringify({ eventName, genreName, participants })
    })
  } catch (e) { console.error(e) }
}
