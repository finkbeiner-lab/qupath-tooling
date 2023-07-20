import qupath.lib.images.servers.ImageServerMetadata
import qupath.lib.images.servers.ImageServerMetadata.Builder



def gui = getQuPath().getInstance()
def project = gui.getProject()
def viewers = gui.getViewers()

def data = viewers.get(0).getImageData()
def server = data.getServer()
def builder = server.createServerBuilder()
//def uris = builder.getURIs()
//def uri = uris.get(0)
//builder.updateURIs([uri:uri.create("abc")])
//def metadata = builder.getMetadata().class.Builder().build()
def metadata = new ImageServerMetadata.Builder(builder.getMetadata()).name("").build()
server = builder.build()
server.setMetadata(metadata)
//server.setMetadata(metadata)
//def hier = new PathObjectHierarchy().setHierarchy(data.getHierarchy())
//def hier = data.getHierarchy()
def type = data.getImageType()

//server.getMetadata().name += ":1"

print data.changes
def newData = new ImageData(server, null, type)
print ([data.changes, newData.changes].toString())
newData.getHierarchy().setHierarchy(data.getHierarchy())
print ([data.changes, newData.changes].toString())

def viewer = viewers.get(1)

Platform.runLater( {
    viewer.setImageData(newData)
})

print data.getServer().getBuilder().getMetadata()
print data.getServer().getBuilder().getURIs()
print newData.getServer().getMetadata()
print newData.getServer().getBuilder().getURIs()

print ([data.changes, newData.changes].toString())

data.setChanged(false)



