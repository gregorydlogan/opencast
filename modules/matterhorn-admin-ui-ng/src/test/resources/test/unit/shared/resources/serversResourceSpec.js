describe('Servers API Resource', function () {
    var ServersResource, $httpBackend;

    beforeEach(module('adminNg.services'));
    beforeEach(module('adminNg.resources'));
    beforeEach(module('ngResource'));

    beforeEach(inject(function (_$httpBackend_, _ServersResource_) {
        $httpBackend  = _$httpBackend_;
        ServersResource = _ServersResource_;
    }));

    describe('#query', function () {
        var sampleJSON = {
            results: [{
                completed: 9,
                running: 2,
                queued: 0,
                meanRunTime: 4,
                meanQueueTime: 200,
                hostname: 'testme1.com',
                type: 'CentOS',
                memory: '2048',
                cores: '4',
                online: true,
                maintenance: false
            }, {
                completed: 291,
                running: 8,
                queued: 19,
                meanRunTime: 2,
                meanQueueTime: 140,
                hostname: 'testme2.com',
                type: 'CentOS',
                memory: '16384',
                cores: '8',
                online: false,
                maintenance: true
            }, {
                completed: 291,
                running: 8,
                queued: 19,
                meanRunTime: 19,
                meanQueueTime: 300,
                hostname: 'testme2.com',
                type: 'CentOS',
                memory: '16384',
                cores: '8',
                online: false,
                maintenance: false
            }]
        };

        var sampleSimpleJSON = {
            results: [{
                completed: 9,
                running: 2,
                queued: 0,
                meanRunTime: 0,
                meanQueueTime: 0,
                hostname: 'testme1.com',
                type: 'CentOS',
                memory: '2048',
                cores: '4',
                online: true,
                maintenance: false
            }, {
                completed: 291,
                running: 8,
                queued: 19,
                meanRunTime: 0,
                meanQueueTime: 0,
                hostname: 'testme2.com',
                type: 'CentOS',
                memory: '16384',
                cores: '8',
                online: false,
                maintenance: true
            }, {
                completed: 291,
                running: 8,
                queued: 19,
                meanRunTime: 0,
                meanQueueTime: 0,
                hostname: 'testme2.com',
                type: 'CentOS',
                memory: '16384',
                cores: '8',
                online: false,
                maintenance: false
            }]
        };

        it('calls the servers service', function () {
            $httpBackend.expectGET('/admin-ng/server/servers.json?full=true').respond(JSON.stringify(sampleJSON));
            ServersResource.query({full: "true"});
            $httpBackend.flush();
        });

        it('handles full=false', function () {
            $httpBackend.expectGET('/admin-ng/server/servers.json?full=false').respond(JSON.stringify(sampleSimpleJSON));
            ServersResource.query({full: "false"});
            $httpBackend.flush();
        });

        it('flattens the JSON data received', function () {
            $httpBackend.whenGET('/admin-ng/server/servers.json?full=true').respond(JSON.stringify(sampleJSON));
            var data = ServersResource.query({full: "true"});
            $httpBackend.flush();
            expect(data.rows.length).toBe(3);
            expect(data.rows[0].id).toBe(sampleJSON.results[0].name);
            expect(data.rows[0].online).toBe(true);
            expect(data.rows[1].online).toBe(false);
            expect(data.rows[2].online).toBe(false);
        });
    });
});
