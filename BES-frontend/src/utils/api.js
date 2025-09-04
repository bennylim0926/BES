const domain = "http://localhost:5050"
export const fetchAllEvents = async () =>{
    try{
        const res = await fetch(`${domain}/api/v1/folders`)
        return await res.json()
      }catch(err){
        console.log(err)
      }
}

export const fetchAllGenres = async () =>{
    
}